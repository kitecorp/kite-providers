package cloud.kitelang.provider.terraform;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import tfplugin5.Tfplugin5.ApplyResourceChange;
import tfplugin5.Tfplugin5.AttributePath;
import tfplugin5.Tfplugin5.DynamicValue;
import tfplugin5.Tfplugin5.PlanResourceChange;
import tfplugin5.Tfplugin5.ReadResource;
import tfplugin5.Tfplugin5.Schema;
import tfplugin5.Tfplugin5.ValidateResourceTypeConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bridges Kite CRUD operations to Terraform's tfplugin5 gRPC protocol.
 *
 * <p>One instance per Terraform resource type. Translates between Kite's camelCase
 * {@code Map<String, Object>} property bags and Terraform's snake_case cty
 * msgpack-encoded {@link DynamicValue} messages.</p>
 *
 * <h3>CRUD mapping to Terraform RPCs</h3>
 * <p>Terraform core always calls {@code ValidateResourceTypeConfig} and
 * {@code PlanResourceChange} before {@code ApplyResourceChange}; providers rely on
 * the plan step to fill in computed values and to flag attributes that force
 * replacement, so the bridge mirrors that sequence:</p>
 * <ul>
 *   <li>{@link #create} &rarr; {@code Validate} + {@code Plan} + {@code Apply} (null prior state)</li>
 *   <li>{@link #read}   &rarr; {@code ReadResource}</li>
 *   <li>{@link #update} &rarr; {@code ReadResource} + {@code Validate} + {@code Plan} + {@code Apply};
 *       when the plan flags {@code requiresReplace} the bridge destroys and recreates
 *       (the Kite provider protocol has no replace concept)</li>
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

    /** Opaque provider-private state — stored across operations, never interpreted. */
    private ByteString privateData = ByteString.EMPTY;

    /**
     * Last state returned by the provider for this resource, or null before create.
     *
     * <p>Used as the prior state when planning updates so the plan sees the real
     * diff (desired vs. last applied) instead of desired vs. desired. In-memory
     * only, like {@link #privateData} — persisting both through the engine across
     * CLI invocations is tracked separately (docs/todo.md P1, prior-state
     * persistence).</p>
     */
    private DynamicValue lastKnownState;

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
     * @param client                 the go-plugin gRPC client for making tfplugin5 calls
     * @param schemaTypeJson         JSON-encoded cty object type for this resource,
     *                               e.g. {@code ["object", {"ami": "string", "instance_type": "string"}]}
     * @param readOnlyAttributeNames snake_case names of computed-only attributes;
     *                               see {@link #readOnlyAttributeNames(Schema.Block)}
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
    public static Set<String> readOnlyAttributeNames(Schema.Block block) {
        return block.getAttributesList().stream()
                .filter(attr -> attr.getComputed() && !attr.getOptional() && !attr.getRequired())
                .map(Schema.Attribute::getName)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Creates a new resource: validate + plan + {@code ApplyResourceChange} with
     * null prior state. The apply uses the plan's planned state and private bytes
     * so computed values the provider filled in at plan time are honoured.
     *
     * @param properties camelCase property map from Kite
     * @return the created resource state in camelCase
     */
    @Override
    public Map<String, Object> create(Map<String, Object> properties) {
        var snakeProps = TerraformPropertyMapper.toSnakeCase(properties);
        var proposed = encodeToDynamicValue(snakeProps);
        var config = encodeToDynamicValue(withoutReadOnlyAttributes(snakeProps));

        validateConfig(config, "create");
        var plan = planChange(nullDynamicValue(), proposed, config, "create");

        log.debug("create {} — sending ApplyResourceChange", tfTypeName);
        var response = applyChange(nullDynamicValue(), config, plan, "create");
        return decodeResponse(response.getNewState());
    }

    /**
     * Reads the current state via {@code ReadResource}.
     *
     * @param properties camelCase property map identifying the resource
     * @return the current resource state in camelCase
     */
    @Override
    public Map<String, Object> read(Map<String, Object> properties) {
        var snakeProps = TerraformPropertyMapper.toSnakeCase(properties);
        var encodedState = encodeToDynamicValue(snakeProps);

        var request = ReadResource.Request.newBuilder()
                .setTypeName(tfTypeName)
                .setCurrentState(encodedState)
                .setPrivate(privateData)
                .build();

        log.debug("read {} — sending ReadResource", tfTypeName);
        var response = client.getStub().readResource(request);
        checkDiagnostics(response.getDiagnosticsList(), "read");

        privateData = response.getPrivate();
        lastKnownState = response.getNewState();
        return decodeResponse(response.getNewState());
    }

    /**
     * Updates a resource: refresh the prior state via {@code ReadResource}, then
     * validate + plan the change. When the plan flags {@code requiresReplace} the
     * bridge destroys the old resource and recreates it (Terraform core semantics —
     * the Kite provider protocol has no replace concept, so the bridge owns this);
     * otherwise the change is applied in place with the plan's planned state.
     *
     * @param properties the desired camelCase property map
     * @return the updated resource state in camelCase
     */
    @Override
    public Map<String, Object> update(Map<String, Object> properties) {
        var snakeProps = TerraformPropertyMapper.toSnakeCase(properties);
        // Proposed state keeps computed values (mirrors Terraform core, which
        // merges prior computed values into the proposal); config must not.
        var proposed = encodeToDynamicValue(snakeProps);
        var config = encodeToDynamicValue(withoutReadOnlyAttributes(snakeProps));

        // Refresh the prior state from the provider. Prefer the last applied state
        // as the read input; without it (fresh process) the desired props are the
        // only available approximation until prior-state persistence lands.
        var readRequest = ReadResource.Request.newBuilder()
                .setTypeName(tfTypeName)
                .setCurrentState(lastKnownState != null ? lastKnownState : proposed)
                .setPrivate(privateData)
                .build();

        log.debug("update {} — refreshing prior state via ReadResource", tfTypeName);
        var readResponse = client.getStub().readResource(readRequest);
        checkDiagnostics(readResponse.getDiagnosticsList(), "update (read prior)");
        privateData = readResponse.getPrivate();
        var priorState = readResponse.getNewState();

        validateConfig(config, "update");
        var plan = planChange(priorState, proposed, config, "update");

        if (plan.getRequiresReplaceCount() > 0) {
            log.info("update {} — plan requires replacement ({}), destroying and recreating",
                    tfTypeName, describeAttributePaths(plan.getRequiresReplaceList()));
            return replace(priorState, config);
        }

        log.debug("update {} — sending ApplyResourceChange", tfTypeName);
        var response = applyChange(priorState, config, plan, "update");
        return decodeResponse(response.getNewState());
    }

    /**
     * Deletes a resource: validate + plan the destroy (null proposed state and
     * config), then {@code ApplyResourceChange} with the plan's output.
     *
     * @param properties camelCase property map identifying the resource to delete
     * @return {@code true} always (TF indicates failure via diagnostics/exceptions)
     */
    @Override
    public boolean delete(Map<String, Object> properties) {
        var snakeProps = TerraformPropertyMapper.toSnakeCase(properties);
        var priorState = encodeToDynamicValue(snakeProps);

        // Validate the last known configurable attributes — a nil config fails
        // required-attribute checks. The destroy plan itself uses the null
        // proposed state and config, matching a resource leaving the config.
        validateConfig(encodeToDynamicValue(withoutReadOnlyAttributes(snakeProps)), "delete");
        var plan = planChange(priorState, nullDynamicValue(), nullDynamicValue(), "delete");

        log.debug("delete {} — sending ApplyResourceChange with null planned state", tfTypeName);
        applyChange(priorState, nullDynamicValue(), plan, "delete");
        lastKnownState = null;
        return true;
    }

    /**
     * Previews a change without applying it: validate + {@code PlanResourceChange}.
     *
     * <p>Computed attributes the provider cannot resolve until apply time come back
     * as the {@link CtyCodec#UNKNOWN} sentinel ("known after apply" in Terraform
     * terms), so {@code kite plan} output can distinguish them from real values.</p>
     *
     * @param priorState    the current camelCase state, or null for a create preview
     * @param proposedState the desired camelCase state
     * @return the planned state in camelCase, with {@link CtyCodec#UNKNOWN} for
     *         values only known after apply
     */
    @Override
    public Map<String, Object> plan(Map<String, Object> priorState, Map<String, Object> proposedState) {
        var snakeProps = TerraformPropertyMapper.toSnakeCase(proposedState);
        var proposed = encodeToDynamicValue(snakeProps);
        var config = encodeToDynamicValue(withoutReadOnlyAttributes(snakeProps));
        var prior = priorState == null
                ? nullDynamicValue()
                : encodeToDynamicValue(TerraformPropertyMapper.toSnakeCase(priorState));

        validateConfig(config, "plan");
        var response = planChange(prior, proposed, config, "plan");
        return decodeResponse(response.getPlannedState());
    }

    // ---------------------------------------------------------------
    // Internal: validate / plan / apply building blocks
    // ---------------------------------------------------------------

    /**
     * Destroys the old resource and creates a replacement with the desired config.
     * Each step is planned before it is applied, exactly like two separate
     * Terraform operations. The recreate proposes the bare config (no computed
     * values carried over) so the provider derives everything fresh.
     */
    private Map<String, Object> replace(DynamicValue priorState, DynamicValue config) {
        // Destroy: proposed state and config are null, mirroring a destroy plan
        var destroyPlan = planChange(priorState, nullDynamicValue(), nullDynamicValue(),
                "update (plan destroy for replace)");
        applyChange(priorState, nullDynamicValue(), destroyPlan, "update (destroy for replace)");

        // Recreate from scratch with the desired configuration
        var createPlan = planChange(nullDynamicValue(), config, config,
                "update (plan create for replace)");
        var response = applyChange(nullDynamicValue(), config, createPlan,
                "update (create for replace)");
        return decodeResponse(response.getNewState());
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
     * Calls {@code ValidateResourceTypeConfig} and throws on ERROR diagnostics.
     *
     * @param config    the cty-encoded configuration to validate
     * @param operation the operation name for error reporting
     */
    private void validateConfig(DynamicValue config, String operation) {
        var request = ValidateResourceTypeConfig.Request.newBuilder()
                .setTypeName(tfTypeName)
                .setConfig(config)
                .build();

        log.debug("{} {} — sending ValidateResourceTypeConfig", operation, tfTypeName);
        var response = client.getStub().validateResourceTypeConfig(request);
        checkDiagnostics(response.getDiagnosticsList(), operation + " (validate)");
    }

    /**
     * Calls {@code PlanResourceChange} with the handler's current private state
     * and throws on ERROR diagnostics.
     *
     * @param priorState       cty-encoded prior state ({@code nil} for create)
     * @param proposedNewState cty-encoded desired state ({@code nil} for destroy)
     * @param config           cty-encoded configuration ({@code nil} for destroy)
     * @param operation        the operation name for error reporting
     * @return the full plan response (planned state, private bytes, requiresReplace)
     */
    private PlanResourceChange.Response planChange(DynamicValue priorState, DynamicValue proposedNewState,
                                                   DynamicValue config, String operation) {
        var request = PlanResourceChange.Request.newBuilder()
                .setTypeName(tfTypeName)
                .setPriorState(priorState)
                .setProposedNewState(proposedNewState)
                .setConfig(config)
                .setPriorPrivate(privateData)
                .build();

        log.debug("{} {} — sending PlanResourceChange", operation, tfTypeName);
        var response = client.getStub().planResourceChange(request);
        checkDiagnostics(response.getDiagnosticsList(), operation + " (plan)");
        return response;
    }

    /**
     * Calls {@code ApplyResourceChange} with the plan's planned state and private
     * bytes, throws on ERROR diagnostics, and records the returned private and
     * new state for subsequent operations.
     *
     * @param priorState cty-encoded prior state
     * @param config     cty-encoded configuration ({@code nil} for destroy)
     * @param plan       the plan response whose output drives the apply
     * @param operation  the operation name for error reporting
     * @return the apply response
     */
    private ApplyResourceChange.Response applyChange(DynamicValue priorState, DynamicValue config,
                                                     PlanResourceChange.Response plan, String operation) {
        var request = ApplyResourceChange.Request.newBuilder()
                .setTypeName(tfTypeName)
                .setPriorState(priorState)
                .setPlannedState(plan.getPlannedState())
                .setConfig(config)
                .setPlannedPrivate(plan.getPlannedPrivate())
                .build();

        var response = client.getStub().applyResourceChange(request);
        checkDiagnostics(response.getDiagnosticsList(), operation);

        privateData = response.getPrivate();
        lastKnownState = response.getNewState();
        return response;
    }

    /**
     * Renders requiresReplace attribute paths for log output,
     * e.g. {@code length} or {@code tags["env"]}.
     */
    private static String describeAttributePaths(List<AttributePath> paths) {
        return paths.stream()
                .map(TerraformResourceTypeHandler::describeAttributePath)
                .collect(Collectors.joining(", "));
    }

    private static String describeAttributePath(AttributePath path) {
        var rendered = new StringBuilder();
        for (var step : path.getStepsList()) {
            switch (step.getSelectorCase()) {
                case ATTRIBUTE_NAME -> {
                    if (!rendered.isEmpty()) {
                        rendered.append('.');
                    }
                    rendered.append(step.getAttributeName());
                }
                case ELEMENT_KEY_STRING -> rendered.append("[\"").append(step.getElementKeyString()).append("\"]");
                case ELEMENT_KEY_INT -> rendered.append('[').append(step.getElementKeyInt()).append(']');
                case SELECTOR_NOT_SET -> rendered.append("<?>");
            }
        }
        return rendered.toString();
    }
}
