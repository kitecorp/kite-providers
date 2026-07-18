package cloud.kitelang.provider.terraform;

import cloud.kitelang.provider.ResourceTypeHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Shared base class for Terraform resource and data source handlers.
 *
 * <p>Centralises the common codec, property conversion, and diagnostics logic
 * used by both {@link TerraformResourceTypeHandler} and
 * {@link TerraformDataSourceHandler}. All protocol traffic goes through the
 * version-agnostic {@link TerraformProviderRpc} facade, so the handlers work
 * identically over tfplugin5 and tfplugin6.</p>
 */
@Slf4j
abstract class AbstractTerraformHandler extends ResourceTypeHandler<Map<String, Object>> {

    protected final String tfTypeName;
    protected final GoPluginClient client;
    protected final String schemaTypeJson;
    protected final CtyCodec codec;

    /**
     * @param tfTypeName     the original TF type name (e.g. {@code "aws_instance"})
     * @param kiteTypeName   the converted Kite type name (e.g. {@code "Instance"})
     * @param client         the go-plugin gRPC client
     * @param schemaTypeJson JSON-encoded cty object type for encode/decode
     */
    protected AbstractTerraformHandler(String tfTypeName, String kiteTypeName,
                                       GoPluginClient client, String schemaTypeJson) {
        super(Map.class, kiteTypeName);
        this.tfTypeName = tfTypeName;
        this.client = client;
        this.schemaTypeJson = schemaTypeJson;
        this.codec = new CtyCodec();
    }

    /**
     * Returns the version-agnostic RPC facade for the wrapped provider process.
     * Resolved per call so the handler carries no protocol-version state.
     *
     * @return the protocol-appropriate RPC implementation
     */
    protected TerraformProviderRpc rpc() {
        return client.rpc();
    }

    /**
     * Encodes a snake_case property map into cty msgpack bytes.
     *
     * @param snakeCaseProps the property map with snake_case keys
     * @return the msgpack-encoded bytes for a {@code DynamicValue}
     */
    protected byte[] encodeToMsgpack(Map<String, Object> snakeCaseProps) {
        return codec.encode(snakeCaseProps, schemaTypeJson);
    }

    /**
     * Returns the msgpack nil encoding of a null cty value.
     *
     * <p>Used as the prior state for create operations and planned state for delete.
     * Terraform providers require a properly encoded nil value rather than an empty
     * byte array (which has no data at all).</p>
     *
     * @return the msgpack nil payload
     */
    protected static byte[] nilMsgpack() {
        try (var packer = org.msgpack.core.MessagePack.newDefaultBufferPacker()) {
            packer.packNil();
            return packer.toByteArray();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("Failed to pack nil value", e);
        }
    }

    /**
     * Decodes cty msgpack state bytes into a camelCase property map.
     *
     * @param stateMsgpack the msgpack bytes from a TF response
     * @return a mutable map with camelCase keys
     */
    protected Map<String, Object> decodeState(byte[] stateMsgpack) {
        var snakeCaseResult = codec.decode(stateMsgpack, schemaTypeJson);
        return TerraformPropertyMapper.toCamelCase(snakeCaseResult);
    }

    /**
     * Checks diagnostics for ERROR-level entries and throws if any are found.
     *
     * <p>WARNING-level diagnostics are logged but do not cause failure.</p>
     *
     * @param diagnostics the diagnostics list from a TF response
     * @param operation   the operation name for error reporting (e.g. "create", "read")
     * @throws RuntimeException if any ERROR-level diagnostic is present
     */
    protected void checkDiagnostics(List<TfDiagnostic> diagnostics, String operation) {
        TerraformDiagnostics.check(diagnostics, operation, tfTypeName);
    }
}
