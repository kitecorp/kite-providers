package cloud.kitelang.provider.terraform;

import cloud.kitelang.provider.ResourceContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import tfplugin5.Tfplugin5.Schema;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bridges Kite CRUD operations to the Terraform plugin protocol.
 *
 * <p>One instance per Terraform resource type. Translates between Kite's camelCase
 * {@code Map<String, Object>} property bags and Terraform's snake_case cty
 * msgpack-encoded values, and speaks whichever protocol version (tfplugin5 or
 * tfplugin6) the go-plugin handshake negotiated via the
 * {@link TerraformProviderRpc} facade.</p>
 *
 * <p>The handler is stateless: prior state and provider-private bytes arrive in
 * the per-operation {@link ResourceContext} (persisted by the engine between
 * invocations) and the private bytes each apply returns go back out through it.
 * A fresh handler in a fresh process therefore operates correctly on resources
 * created by a previous process instance.</p>
 *
 * <p>The engine-persisted private bytes carry a {@link SchemaVersionEnvelope}
 * recording the resource type's schema version at write time. When a newer
 * provider release (higher schema version) later operates on that state, the
 * handler first calls {@code UpgradeResourceState} with the stored state in raw
 * JSON form — mirroring Terraform core, which upgrades state-file entries whose
 * recorded {@code schema_version} lags the provider's — and every subsequent
 * RPC uses the upgraded state (kitecorp/kite-providers#5). The envelope never
 * reaches the wrapped provider: its own private bytes are extracted before any
 * TF RPC and re-enveloped on the way back to the engine.</p>
 *
 * <h3>CRUD mapping to Terraform RPCs</h3>
 * <p>Terraform core always validates the resource config and calls
 * {@code PlanResourceChange} before {@code ApplyResourceChange}; providers rely on
 * the plan step to fill in computed values and to flag attributes that force
 * replacement, so the bridge mirrors that sequence:</p>
 * <ul>
 *   <li>{@link #create} &rarr; {@code Validate} + {@code Plan} + {@code Apply} (null prior state)</li>
 *   <li>{@link #read}   &rarr; {@code ReadResource} with the stored private bytes</li>
 *   <li>{@link #importResource} &rarr; {@code ImportResourceState} + {@code ReadResource}
 *       refresh — adopts a pre-existing resource by cloud id (Kite's {@code @existing("<id>")})</li>
 *   <li>{@link #update} &rarr; {@code Validate} + {@code Plan} + {@code Apply} using the
 *       engine-stored prior state; when the plan flags {@code requiresReplace} the bridge
 *       destroys and recreates (the Kite provider protocol has no replace concept)</li>
 *   <li>{@link #delete} &rarr; {@code Validate} + {@code Plan} + {@code Apply} (null planned state)</li>
 *   <li>{@link #plan}   &rarr; {@code Validate} + {@code Plan} only — diff preview where
 *       computed values surface as {@link CtyCodec#UNKNOWN} ("known after apply")</li>
 * </ul>
 *
 * @see CtyCodec
 * @see TerraformPropertyMapper
 */
@Slf4j
public class TerraformResourceTypeHandler extends AbstractTerraformHandler {

    /** Serialises stored state maps into the raw JSON form UpgradeResourceState takes. */
    private static final ObjectMapper JSON = new ObjectMapper();

    /** The resource type's current schema version, from the provider's schema. */
    private final long schemaVersion;

    /**
     * Creates a handler for a single Terraform resource type.
     *
     * @param tfTypeName             the original TF resource type name (e.g. {@code "aws_instance"})
     * @param kiteTypeName           the converted Kite type name (e.g. {@code "Instance"})
     * @param client                 the go-plugin gRPC client for making Terraform protocol calls
     * @param schemaTypeJson         JSON-encoded cty object type for this resource,
     *                               e.g. {@code ["object", {"ami": "string", "instance_type": "string"}]}
     * @param readOnlyAttributeNames snake_case names of computed-only attributes;
     *                               see {@link #readOnlyAttributeNames(TfBlock)}
     * @param schemaVersion          the resource type's current schema version
     *                               ({@code Schema.version} in the TF provider schema)
     */
    public TerraformResourceTypeHandler(String tfTypeName, String kiteTypeName,
                                        GoPluginClient client, String schemaTypeJson,
                                        Set<String> readOnlyAttributeNames, long schemaVersion) {
        super(tfTypeName, kiteTypeName, client, schemaTypeJson, readOnlyAttributeNames);
        this.schemaVersion = schemaVersion;
    }

    /**
     * Extracts the read-only attribute names from a TF schema block: attributes
     * that are computed but neither optional nor required cannot legally appear
     * in configuration (an optional+computed attribute can).
     *
     * @param block the resource's schema block
     * @return immutable set of snake_case attribute names
     */
    public static Set<String> readOnlyAttributeNames(TfBlock block) {
        return block.attributes().stream()
                .filter(attr -> attr.computed() && !attr.optional() && !attr.required())
                .map(TfAttribute::name)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Convenience overload for callers holding a raw tfplugin5 schema block
     * (e.g. protocol-5 raw-RPC tests).
     *
     * @param block the resource's tfplugin5 schema block
     * @return immutable set of snake_case attribute names
     */
    public static Set<String> readOnlyAttributeNames(Schema.Block block) {
        return readOnlyAttributeNames(Tfplugin5Rpc.toTfBlock(block));
    }

    // ---------------------------------------------------------------
    // Legacy single-argument entry points — no stored state available
    // ---------------------------------------------------------------

    @Override
    public Map<String, Object> create(Map<String, Object> properties) {
        return create(properties, ResourceContext.empty());
    }

    @Override
    public Map<String, Object> read(Map<String, Object> properties) {
        return read(properties, ResourceContext.empty());
    }

    @Override
    public Map<String, Object> update(Map<String, Object> properties) {
        return update(properties, ResourceContext.empty());
    }

    @Override
    public boolean delete(Map<String, Object> properties) {
        return delete(properties, ResourceContext.empty());
    }

    // ---------------------------------------------------------------
    // Context-carrying CRUD — engine-stored prior state + private bytes
    // ---------------------------------------------------------------

    /**
     * Creates a new resource: validate + plan + {@code ApplyResourceChange} with
     * null prior state. The apply uses the plan's planned state and private bytes
     * so computed values the provider filled in at plan time are honoured. The
     * private bytes the provider returns go back to the engine via the context.
     *
     * @param properties camelCase property map from Kite
     * @param context    private bytes in (normally empty for create) and out
     * @return the created resource state in camelCase
     */
    @Override
    public Map<String, Object> create(Map<String, Object> properties,
                                      ResourceContext<Map<String, Object>> context) {
        var snakeProps = TerraformPropertyMapper.toSnakeCase(properties);
        var proposed = encodeToMsgpack(snakeProps);
        var config = encodeToMsgpack(withoutReadOnlyAttributes(snakeProps));
        var stored = SchemaVersionEnvelope.unwrap(context.privateData());

        validateConfig(config, "create");
        var plan = planChange(nilMsgpack(), proposed, config, stored.providerBytes(), "create");

        log.debug("create {} — sending ApplyResourceChange", tfTypeName);
        var result = applyChange(nilMsgpack(), config, plan, "create");
        returnPrivateData(context, result.privateBytes());
        return decodeState(result.state());
    }

    /**
     * Reads the current state via {@code ReadResource}, passing the engine-stored
     * private bytes and returning the refreshed ones via the context.
     *
     * @param properties camelCase property map identifying the resource
     * @param context    private bytes in and out
     * @return the current resource state in camelCase
     */
    @Override
    public Map<String, Object> read(Map<String, Object> properties,
                                    ResourceContext<Map<String, Object>> context) {
        var snakeProps = TerraformPropertyMapper.toSnakeCase(properties);
        var stored = SchemaVersionEnvelope.unwrap(context.privateData());
        var prior = restorePriorState(snakeProps, stored.schemaVersion(), "read");

        log.debug("read {} — sending ReadResource", tfTypeName);
        var result = rpc().readResource(tfTypeName, prior.msgpack(), stored.providerBytes());
        checkDiagnostics(result.diagnostics(), "read");

        returnPrivateData(context, result.privateBytes());
        return decodeState(result.state());
    }

    /**
     * Imports (adopts) a pre-existing resource by its cloud identifier, mirroring
     * Terraform core's import sequence: {@code ImportResourceState} returns a
     * minimal state (often just the id) plus private bytes, which a follow-up
     * {@code ReadResource} refreshes into the full resource state. The refreshed
     * state and private bytes go back to the engine exactly like a create's,
     * so an adopted resource persists indistinguishably from a created one.
     *
     * @param importId the provider-interpreted identifier (instance id, ARN, ...)
     * @param context  private bytes out (nothing is stored yet on import)
     * @return the adopted resource state in camelCase, or null when the provider
     *         imported nothing or the refresh found no remote object for the id
     */
    @Override
    public Map<String, Object> importResource(String importId,
                                              ResourceContext<Map<String, Object>> context) {
        log.debug("import {} — sending ImportResourceState for id {}", tfTypeName, importId);
        var imported = rpc().importResourceState(tfTypeName, importId);
        checkDiagnostics(imported.diagnostics(), "import");
        if (imported.state() == null) {
            // The provider imported nothing of this type for the id
            return null;
        }

        log.debug("import {} — refreshing imported state via ReadResource", tfTypeName);
        var refreshed = rpc().readResource(tfTypeName, imported.state(), imported.privateBytes());
        checkDiagnostics(refreshed.diagnostics(), "import (refresh)");

        // A nil/absent refreshed state means no remote object exists for the
        // id — "cannot import non-existent remote object" in Terraform terms.
        // Passthrough-style providers accept any id at the import step, so the
        // refresh is where a bogus id actually surfaces.
        if (refreshed.state() == null || refreshed.state().length == 0) {
            return null;
        }
        var state = decodeState(refreshed.state());
        if (state == null || state.isEmpty()) {
            return null;
        }
        returnPrivateData(context, refreshed.privateBytes());
        return state;
    }

    /**
     * Updates a resource using the engine-stored prior state: validate + plan the
     * change against what was actually applied last time. When the plan flags
     * {@code requiresReplace} the bridge destroys the old resource and recreates
     * it (Terraform core semantics — the Kite provider protocol has no replace
     * concept, so the bridge owns this); otherwise the change is applied in place
     * with the plan's planned state.
     *
     * <p>Without engine-supplied prior state (direct handler invocation) the
     * desired properties are the only available approximation, which degenerates
     * the plan to a no-diff; correct diffs require the engine to supply the
     * stored state.</p>
     *
     * @param properties the desired camelCase property map
     * @param context    prior state + private bytes in, updated private bytes out
     * @return the updated resource state in camelCase
     */
    @Override
    public Map<String, Object> update(Map<String, Object> properties,
                                      ResourceContext<Map<String, Object>> context) {
        var snakeProps = TerraformPropertyMapper.toSnakeCase(properties);
        // Proposed state keeps computed values (mirrors Terraform core, which
        // merges prior computed values into the proposal); config must not.
        var proposed = encodeToMsgpack(snakeProps);
        var config = encodeToMsgpack(withoutReadOnlyAttributes(snakeProps));
        var stored = SchemaVersionEnvelope.unwrap(context.privateData());

        var priorState = context.priorState() != null
                ? restorePriorState(TerraformPropertyMapper.toSnakeCase(context.priorState()),
                        stored.schemaVersion(), "update").msgpack()
                : proposed;
        var priorPrivate = stored.providerBytes();

        validateConfig(config, "update");
        var plan = planChange(priorState, proposed, config, priorPrivate, "update");

        if (!plan.requiresReplace().isEmpty()) {
            log.info("update {} — plan requires replacement ({}), destroying and recreating",
                    tfTypeName, describeAttributePaths(plan.requiresReplace()));
            return replace(priorState, config, priorPrivate, context);
        }

        log.debug("update {} — sending ApplyResourceChange", tfTypeName);
        var result = applyChange(priorState, config, plan, "update");
        returnPrivateData(context, result.privateBytes());
        return decodeState(result.state());
    }

    /**
     * Deletes a resource: validate + plan the destroy (null proposed state and
     * config) with the engine-stored private bytes, then {@code ApplyResourceChange}
     * with the plan's output. The engine passes the stored prior state as the
     * property map, so the destroy operates on what was actually applied.
     *
     * @param properties camelCase prior state identifying the resource to delete
     * @param context    private bytes in, post-destroy private bytes out
     * @return {@code true} always (TF indicates failure via diagnostics/exceptions)
     */
    @Override
    public boolean delete(Map<String, Object> properties,
                          ResourceContext<Map<String, Object>> context) {
        var stored = SchemaVersionEnvelope.unwrap(context.privateData());
        // The engine passes the stored prior state as the property map; when
        // that state predates the current schema it may not even encode under
        // it, so the upgrade must run before any cty encoding happens.
        var prior = restorePriorState(TerraformPropertyMapper.toSnakeCase(properties),
                stored.schemaVersion(), "delete");

        // Validate the last known configurable attributes — a nil config fails
        // required-attribute checks. The destroy plan itself uses the null
        // proposed state and config, matching a resource leaving the config.
        validateConfig(encodeToMsgpack(withoutReadOnlyAttributes(prior.snakeProps())), "delete");
        var plan = planChange(prior.msgpack(), nilMsgpack(), nilMsgpack(), stored.providerBytes(), "delete");

        log.debug("delete {} — sending ApplyResourceChange with null planned state", tfTypeName);
        var result = applyChange(prior.msgpack(), nilMsgpack(), plan, "delete");
        returnPrivateData(context, result.privateBytes());
        return true;
    }

    /**
     * Previews a change without applying it: validate + {@code PlanResourceChange}.
     *
     * <p>Computed attributes the provider cannot resolve until apply time come back
     * as the {@link CtyCodec#UNKNOWN} sentinel ("known after apply" in Terraform
     * terms), so {@code kite plan} output can distinguish them from real values.
     * The preview runs without stored private bytes — the engine computes diffs
     * itself and only calls this dormant path without persistence involved.</p>
     *
     * @param priorState    the current camelCase state, or null for a create preview
     * @param proposedState the desired camelCase state
     * @return the planned state in camelCase, with {@link CtyCodec#UNKNOWN} for
     *         values only known after apply
     */
    @Override
    public Map<String, Object> plan(Map<String, Object> priorState, Map<String, Object> proposedState) {
        var snakeProps = TerraformPropertyMapper.toSnakeCase(proposedState);
        var proposed = encodeToMsgpack(snakeProps);
        var config = encodeToMsgpack(withoutReadOnlyAttributes(snakeProps));
        var prior = priorState == null
                ? nilMsgpack()
                : encodeToMsgpack(TerraformPropertyMapper.toSnakeCase(priorState));

        validateConfig(config, "plan");
        var plan = planChange(prior, proposed, config, new byte[0], "plan");
        return decodeState(plan.plannedState());
    }

    // ---------------------------------------------------------------
    // Internal: validate / plan / apply building blocks
    // ---------------------------------------------------------------

    /**
     * Destroys the old resource and creates a replacement with the desired config.
     * Each step is planned before it is applied, exactly like two separate
     * Terraform operations. The recreate proposes the bare config (no computed
     * values carried over) and starts from empty private bytes — nothing of the
     * destroyed resource survives. The recreate's private bytes go back to the
     * engine via the context.
     */
    private Map<String, Object> replace(byte[] priorState, byte[] config, byte[] priorPrivate,
                                        ResourceContext<Map<String, Object>> context) {
        // Destroy: proposed state and config are null, mirroring a destroy plan
        var destroyPlan = planChange(priorState, nilMsgpack(), nilMsgpack(),
                priorPrivate, "update (plan destroy for replace)");
        applyChange(priorState, nilMsgpack(), destroyPlan, "update (destroy for replace)");

        // Recreate from scratch with the desired configuration
        var createPlan = planChange(nilMsgpack(), config, config,
                new byte[0], "update (plan create for replace)");
        var result = applyChange(nilMsgpack(), config, createPlan,
                "update (create for replace)");
        returnPrivateData(context, result.privateBytes());
        return decodeState(result.state());
    }

    // ---------------------------------------------------------------
    // Internal: schema version envelope + state upgrades
    // ---------------------------------------------------------------

    /**
     * Hands the provider's private bytes back to the engine wrapped in the
     * schema-version envelope, recording that the state persisted alongside
     * them was written under this handler's current schema version.
     */
    private void returnPrivateData(ResourceContext<Map<String, Object>> context, byte[] providerBytes) {
        context.returnPrivateData(SchemaVersionEnvelope.wrap(schemaVersion, providerBytes));
    }

    /**
     * Stored prior state made compliant with the current schema.
     *
     * @param msgpack    the cty msgpack encoding to send as prior/current state
     * @param snakeProps the matching snake_case map, for building config values
     */
    private record RestoredPriorState(byte[] msgpack, Map<String, Object> snakeProps) {
    }

    /**
     * Restores engine-stored prior state for use under the current schema.
     *
     * <p>When the stored schema version lags the current one, the provider
     * upgrades the state via {@code UpgradeResourceState}; the request carries
     * the state as raw JSON because no cty schema exists here for the old
     * version (mirrors Terraform core, which stores state as JSON for exactly
     * this reason). A matching or {@linkplain SchemaVersionEnvelope#UNKNOWN_VERSION
     * unknown} version encodes the map directly — unknown means the state
     * predates version recording, and guessing a version could run the wrong
     * upgraders. A version <em>newer</em> than the current schema means the
     * provider binary was downgraded, which Terraform refuses too.</p>
     *
     * @param snakeProps    the stored state as a snake_case map
     * @param storedVersion the schema version recorded with the state
     * @param operation     the operation name for error reporting
     * @return the schema-compliant prior state in both encodings
     */
    private RestoredPriorState restorePriorState(Map<String, Object> snakeProps, long storedVersion,
                                                 String operation) {
        if (storedVersion == SchemaVersionEnvelope.UNKNOWN_VERSION || storedVersion == schemaVersion) {
            return new RestoredPriorState(encodeToMsgpack(snakeProps), snakeProps);
        }
        if (storedVersion > schemaVersion) {
            throw new IllegalStateException(
                    ("Stored state for %s was written with schema version %d, but this provider "
                            + "release supports schema version %d. State downgrades are not supported "
                            + "- use a provider release with schema version %d or newer.")
                            .formatted(tfTypeName, storedVersion, schemaVersion, storedVersion));
        }

        log.info("{} {} — upgrading stored state from schema version {} to {}",
                operation, tfTypeName, storedVersion, schemaVersion);
        var result = rpc().upgradeResourceState(tfTypeName, storedVersion, toRawStateJson(snakeProps));
        checkDiagnostics(result.diagnostics(), operation + " (upgrade state)");
        var upgraded = result.upgradedState();
        return new RestoredPriorState(upgraded, codec.decode(upgraded, schemaTypeJson));
    }

    /**
     * Serialises a snake_case state map into the raw JSON document form of
     * {@code UpgradeResourceState.Request.raw_state}. Deliberately schema-free:
     * the stored state may contain attributes the current schema no longer
     * knows, and only the provider can interpret them.
     */
    private static byte[] toRawStateJson(Map<String, Object> snakeProps) {
        try {
            return JSON.writeValueAsBytes(snakeProps);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to serialise stored state to JSON for upgrade", e);
        }
    }

    /**
     * Validates the resource configuration and throws on ERROR diagnostics.
     *
     * @param config    the cty msgpack configuration to validate
     * @param operation the operation name for error reporting
     */
    private void validateConfig(byte[] config, String operation) {
        log.debug("{} {} — sending resource config validation", operation, tfTypeName);
        var diagnostics = rpc().validateResourceConfig(tfTypeName, config);
        checkDiagnostics(diagnostics, operation + " (validate)");
    }

    /**
     * Calls {@code PlanResourceChange} and throws on ERROR diagnostics.
     *
     * @param priorState       cty msgpack prior state (nil for create)
     * @param proposedNewState cty msgpack desired state (nil for destroy)
     * @param config           cty msgpack configuration (nil for destroy)
     * @param priorPrivate     provider-private bytes recorded at the last apply
     * @param operation        the operation name for error reporting
     * @return the full plan result (planned state, private bytes, requiresReplace)
     */
    private TerraformProviderRpc.PlanResult planChange(byte[] priorState, byte[] proposedNewState,
                                                       byte[] config, byte[] priorPrivate,
                                                       String operation) {
        log.debug("{} {} — sending PlanResourceChange", operation, tfTypeName);
        var plan = rpc().planResourceChange(tfTypeName, priorState, proposedNewState, config, priorPrivate);
        checkDiagnostics(plan.diagnostics(), operation + " (plan)");
        return plan;
    }

    /**
     * Calls {@code ApplyResourceChange} with the plan's planned state and private
     * bytes and throws on ERROR diagnostics. Callers pull the returned state and
     * private bytes from the result.
     *
     * @param priorState cty msgpack prior state
     * @param config     cty msgpack configuration (nil for destroy)
     * @param plan       the plan result whose output drives the apply
     * @param operation  the operation name for error reporting
     * @return the apply result
     */
    private TerraformProviderRpc.StateResult applyChange(byte[] priorState, byte[] config,
                                                         TerraformProviderRpc.PlanResult plan,
                                                         String operation) {
        var result = rpc().applyResourceChange(tfTypeName, priorState, plan.plannedState(),
                config, plan.plannedPrivate());
        checkDiagnostics(result.diagnostics(), operation);
        return result;
    }

    /**
     * Renders requiresReplace attribute paths for log output,
     * e.g. {@code length} or {@code tags["env"]}.
     */
    private static String describeAttributePaths(List<TfAttributePath> paths) {
        return paths.stream()
                .map(TfAttributePath::render)
                .collect(Collectors.joining(", "));
    }
}
