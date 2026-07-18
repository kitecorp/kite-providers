package cloud.kitelang.provider.terraform;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Bridges Kite read-only data source queries to Terraform's {@code ReadDataSource} RPC.
 *
 * <p>Data sources in Terraform are read-only — they fetch existing infrastructure state
 * without creating, updating, or deleting anything. In Kite, these correspond to
 * {@code @existing} resources without an ID argument.</p>
 *
 * <p>Only {@link #read} is meaningful. {@link #create}, {@link #update}, and {@link #delete}
 * throw {@link UnsupportedOperationException} since data sources are inherently read-only.</p>
 *
 * @see CtyCodec
 * @see TerraformPropertyMapper
 */
@Slf4j
public class TerraformDataSourceHandler extends AbstractTerraformHandler {

    /**
     * Creates a handler for a single Terraform data source type.
     *
     * @param tfTypeName     the original TF data source type name (e.g. {@code "aws_ami"})
     * @param kiteTypeName   the converted Kite type name (e.g. {@code "Ami"})
     * @param client         the go-plugin gRPC client for making Terraform protocol calls
     * @param schemaTypeJson JSON-encoded cty object type for this data source
     */
    public TerraformDataSourceHandler(String tfTypeName, String kiteTypeName,
                                      GoPluginClient client, String schemaTypeJson) {
        super(tfTypeName, kiteTypeName, client, schemaTypeJson);
    }

    /**
     * Reads a data source via {@code ReadDataSource}.
     *
     * @param properties camelCase property map with query parameters
     * @return the data source state in camelCase
     */
    @Override
    public Map<String, Object> read(Map<String, Object> properties) {
        var snakeProps = TerraformPropertyMapper.toSnakeCase(properties);

        log.debug("read data source {} — sending ReadDataSource", tfTypeName);
        var result = rpc().readDataSource(tfTypeName, encodeToMsgpack(snakeProps));
        checkDiagnostics(result.diagnostics(), "read");

        return decodeState(result.state());
    }

    /** Data sources are read-only. */
    @Override
    public Map<String, Object> create(Map<String, Object> properties) {
        throw new UnsupportedOperationException(
                "Data source '%s' is read-only — create is not supported".formatted(tfTypeName));
    }

    /** Data sources are read-only. */
    @Override
    public Map<String, Object> update(Map<String, Object> properties) {
        throw new UnsupportedOperationException(
                "Data source '%s' is read-only — update is not supported".formatted(tfTypeName));
    }

    /** Data sources are read-only. */
    @Override
    public boolean delete(Map<String, Object> properties) {
        throw new UnsupportedOperationException(
                "Data source '%s' is read-only — delete is not supported".formatted(tfTypeName));
    }
}
