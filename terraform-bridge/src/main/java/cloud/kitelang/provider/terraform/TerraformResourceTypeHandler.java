package cloud.kitelang.provider.terraform;

import cloud.kitelang.provider.ResourceContext;
import lombok.extern.slf4j.Slf4j;
import tfplugin5.Tfplugin5.Schema;

import java.util.LinkedHashMap;
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
 * <h3>CRUD mapping to Terraform RPCs</h3>
 * <p>Terraform core always validates the resource config and calls
 * {@code PlanResourceChange} before {@code ApplyResourceChange}; providers rely on
 * the plan step to fill in computed values and to flag attributes that force
 * replacement, so the bridge mirrors that sequence:</p>
 * <ul>
 *   <li>{@link #create} &rarr; {@code Validate} + {@code Plan} + {@code Apply} (null prior state)</li>
 *   <li>{@link #read}   &rarr; {@code ReadResource} with the stored private bytes</li>
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

    /**
     * snake_case names of read-only (computed-only) attributes, e.g. {@code id}.
     *
     * <p>Terraform core never puts these in the {@code config} it sends to
     * validate/plan — providers reject non-null values for them with "Invalid
     * Configuration for Read-Only Attribute". The bridge nulls them out the
     * same way before building a config value.</p>
     */
    private final Set<String> readOnlyAttributeNames;

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
     */
    public TerraformResourceTypeHandler(String tfTypeName, String kiteTypeName,
                                        GoPluginClient client, String schemaTypeJson,
                                        Set<String> readOnlyAttributeNames) {
        super(tfTypeName, kiteTypeName, client, schemaTypeJson);
        this.readOnlyAttributeNames = Set.copyOf(readOnlyAttributeNames);
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

        validateConfig(config, "create");
        var plan = planChange(nilMsgpack(), proposed, config, context.privateData(), "create");

        log.debug("create {} — sending ApplyResourceChange", tfTypeName);
        var result = applyChange(nilMsgpack(), config, plan, "create");
        context.returnPrivateData(result.privateBytes());
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

        log.debug("read {} — sending ReadResource", tfTypeName);
        var result = rpc().readResource(tfTypeName, encodeToMsgpack(snakeProps), context.privateData());
        checkDiagnostics(result.diagnostics(), "read");

        context.returnPrivateData(result.privateBytes());
        return decodeState(result.state());
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

        var priorState = context.priorState() != null
                ? encodeToMsgpack(TerraformPropertyMapper.toSnakeCase(context.priorState()))
                : proposed;
        var priorPrivate = context.privateData();

        validateConfig(config, "update");
        var plan = planChange(priorState, proposed, config, priorPrivate, "update");

        if (!plan.requiresReplace().isEmpty()) {
            log.info("update {} — plan requires replacement ({}), destroying and recreating",
                    tfTypeName, describeAttributePaths(plan.requiresReplace()));
            return replace(priorState, config, priorPrivate, context);
        }

        log.debug("update {} — sending ApplyResourceChange", tfTypeName);
        var result = applyChange(priorState, config, plan, "update");
        context.returnPrivateData(result.privateBytes());
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
        var snakeProps = TerraformPropertyMapper.toSnakeCase(properties);
        var priorState = encodeToMsgpack(snakeProps);

        // Validate the last known configurable attributes — a nil config fails
        // required-attribute checks. The destroy plan itself uses the null
        // proposed state and config, matching a resource leaving the config.
        validateConfig(encodeToMsgpack(withoutReadOnlyAttributes(snakeProps)), "delete");
        var plan = planChange(priorState, nilMsgpack(), nilMsgpack(), context.privateData(), "delete");

        log.debug("delete {} — sending ApplyResourceChange with null planned state", tfTypeName);
        var result = applyChange(priorState, nilMsgpack(), plan, "delete");
        context.returnPrivateData(result.privateBytes());
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
        context.returnPrivateData(result.privateBytes());
        return decodeState(result.state());
    }

    /**
     * Returns a copy of the property map with read-only (computed-only)
     * attributes nulled, making it a legal Terraform configuration value.
     * Terraform core strips these before validate/plan; providers reject
     * configs that set them.
     */
    private Map<String, Object> withoutReadOnlyAttributes(Map<String, Object> snakeProps) {
        if (readOnlyAttributeNames.isEmpty()) {
            return snakeProps;
        }
        var configProps = new LinkedHashMap<>(snakeProps);
        readOnlyAttributeNames.forEach(attribute -> configProps.put(attribute, null));
        return configProps;
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
