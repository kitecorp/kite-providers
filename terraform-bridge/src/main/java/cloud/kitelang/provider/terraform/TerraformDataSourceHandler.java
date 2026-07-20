package cloud.kitelang.provider.terraform;
import cloud.kitelang.tfplugin.CtyCodec;
import cloud.kitelang.tfplugin.GoPluginClient;
import cloud.kitelang.tfplugin.TfBlock;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;

/**
 * Bridges Kite read-only data source queries to Terraform's {@code ReadDataSource} RPC.
 *
 * <p>Data sources in Terraform are read-only — they fetch existing infrastructure state
 * without creating, updating, or deleting anything. The Kite provider protocol has no
 * separate data-source concept, so the bridge surfaces each TF data source as a resource
 * type whose whole lifecycle degenerates to the lookup — mirroring how Terraform itself
 * stores data sources in state:</p>
 * <ul>
 *   <li>{@link #create} — first appearance in the configuration: perform the read; the
 *       result becomes the stored state (TF stores data sources in state after reading)</li>
 *   <li>{@link #read}   — refresh: re-run the lookup</li>
 *   <li>{@link #update} — query changed: re-run the lookup with the new config</li>
 *   <li>{@link #delete} — removal from configuration only drops the stored state; there is
 *       nothing to destroy, so no provider RPC is made</li>
 *   <li>{@link #plan}   — plan-time resolution: run the lookup and return actual values,
 *       so downstream references resolve during planning (Terraform core reads data
 *       sources during plan whenever their configuration is fully known)</li>
 * </ul>
 *
 * <p>Every lookup mirrors Terraform core's sequence: the data source config is validated
 * (t5 {@code ValidateDataSourceConfig} / t6 {@code ValidateDataResourceConfig}) before
 * {@code ReadDataSource}, and computed-only attributes are nulled out of the config the
 * same way core strips them.</p>
 *
 * @see CtyCodec
 * @see TerraformPropertyMapper
 */
@Slf4j
public class TerraformDataSourceHandler extends AbstractTerraformHandler {

    /**
     * Creates a handler for a single Terraform data source type.
     *
     * @param tfTypeName             the original TF data source type name (e.g. {@code "aws_ami"})
     * @param kiteTypeName           the converted Kite type name (e.g. {@code "AmiData"})
     * @param client                 the go-plugin gRPC client for making Terraform protocol calls
     * @param schemaTypeJson         JSON-encoded cty object type for this data source
     * @param readOnlyAttributeNames snake_case names of computed-only attributes; see
     *                               {@link TerraformResourceTypeHandler#readOnlyAttributeNames(TfBlock)}
     */
    public TerraformDataSourceHandler(String tfTypeName, String kiteTypeName,
                                      GoPluginClient client, String schemaTypeJson,
                                      Set<String> readOnlyAttributeNames) {
        super(tfTypeName, kiteTypeName, client, schemaTypeJson, readOnlyAttributeNames);
    }

    /**
     * @param apiSchema the structured schema served over {@code GetProviderSchema}
     *                  so per-property flags (notably {@code sensitive}) reach
     *                  the engine (kitecorp/kite-providers#6); null falls back
     *                  to the SDK shell schema
     */
    public TerraformDataSourceHandler(String tfTypeName, String kiteTypeName,
                                      GoPluginClient client, String schemaTypeJson,
                                      Set<String> readOnlyAttributeNames,
                                      cloud.kitelang.api.schema.Schema apiSchema) {
        super(tfTypeName, kiteTypeName, client, schemaTypeJson, readOnlyAttributeNames, apiSchema);
    }

    /**
     * Reads a data source: validate + {@code ReadDataSource}.
     *
     * @param properties camelCase property map with query parameters
     * @return the data source state in camelCase
     */
    @Override
    public Map<String, Object> read(Map<String, Object> properties) {
        return readDataSource(properties, "read");
    }

    /**
     * A data source entering the configuration is just its first read — the
     * result is what the engine persists as this "resource"'s state.
     */
    @Override
    public Map<String, Object> create(Map<String, Object> properties) {
        return readDataSource(properties, "create");
    }

    /**
     * A changed data source query re-runs the lookup; nothing in the cloud is
     * mutated.
     */
    @Override
    public Map<String, Object> update(Map<String, Object> properties) {
        return readDataSource(properties, "update");
    }

    /**
     * Removing a data source from the configuration only forgets its stored
     * state — there is nothing to destroy, so no provider RPC is made.
     */
    @Override
    public boolean delete(Map<String, Object> properties) {
        log.debug("delete data source {} — dropping state only, no provider call", tfTypeName);
        return true;
    }

    /**
     * Plan-time resolution: performs the actual lookup so the planned state
     * carries real values (not "known after apply"), making the result
     * referenceable by downstream resources during planning.
     *
     * @param priorState    the previously read state, unused — a lookup is always re-run
     * @param proposedState the desired camelCase query configuration
     * @return the freshly read data source state in camelCase
     */
    @Override
    public Map<String, Object> plan(Map<String, Object> priorState, Map<String, Object> proposedState) {
        return readDataSource(proposedState, "plan");
    }

    /**
     * Runs the full lookup sequence Terraform core uses for data sources:
     * config validation followed by {@code ReadDataSource}, with computed-only
     * attributes nulled out of the config value.
     *
     * @param properties camelCase property map with query parameters
     * @param operation  the operation name for error reporting
     * @return the data source state in camelCase
     */
    private Map<String, Object> readDataSource(Map<String, Object> properties, String operation) {
        var snakeProps = TerraformPropertyMapper.toSnakeCase(properties);
        var config = encodeToMsgpack(withoutReadOnlyAttributes(snakeProps));

        log.debug("{} data source {} — sending data source config validation", operation, tfTypeName);
        var validation = rpc().validateDataSourceConfig(tfTypeName, config);
        checkDiagnostics(validation, operation + " (validate)");

        log.debug("{} data source {} — sending ReadDataSource", operation, tfTypeName);
        var result = rpc().readDataSource(tfTypeName, config);
        checkDiagnostics(result.diagnostics(), operation);

        return decodeState(result.state());
    }
}
