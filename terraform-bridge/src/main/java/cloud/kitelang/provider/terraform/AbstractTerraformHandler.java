package cloud.kitelang.provider.terraform;

import cloud.kitelang.provider.ResourceTypeHandler;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import tfplugin5.Tfplugin5.Diagnostic;
import tfplugin5.Tfplugin5.DynamicValue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared base class for Terraform resource and data source handlers.
 *
 * <p>Centralises the common codec, property conversion, and diagnostics logic
 * used by both {@link TerraformResourceTypeHandler} and
 * {@link TerraformDataSourceHandler}.</p>
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
     * Encodes a snake_case property map into a {@link DynamicValue} with msgpack bytes.
     *
     * @param snakeCaseProps the property map with snake_case keys
     * @return a {@code DynamicValue} containing the msgpack-encoded bytes
     */
    protected DynamicValue encodeToDynamicValue(Map<String, Object> snakeCaseProps) {
        var bytes = codec.encode(snakeCaseProps, schemaTypeJson);
        return DynamicValue.newBuilder()
                .setMsgpack(ByteString.copyFrom(bytes))
                .build();
    }

    /**
     * Creates a {@link DynamicValue} containing msgpack nil (null cty value).
     *
     * <p>Used as the prior state for create operations and planned state for delete.
     * Terraform providers require a properly encoded nil value rather than an empty
     * {@code DynamicValue} (which has no data bytes at all).</p>
     *
     * @return a {@code DynamicValue} with msgpack nil payload
     */
    protected static DynamicValue nullDynamicValue() {
        try (var packer = org.msgpack.core.MessagePack.newDefaultBufferPacker()) {
            packer.packNil();
            return DynamicValue.newBuilder()
                    .setMsgpack(ByteString.copyFrom(packer.toByteArray()))
                    .build();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("Failed to pack nil value", e);
        }
    }

    /**
     * Decodes a {@link DynamicValue} response into a camelCase property map.
     *
     * @param dynamicValue the TF response containing msgpack bytes
     * @return a mutable map with camelCase keys
     */
    protected Map<String, Object> decodeResponse(DynamicValue dynamicValue) {
        var bytes = dynamicValue.getMsgpack().toByteArray();
        var snakeCaseResult = codec.decode(bytes, schemaTypeJson);
        return TerraformPropertyMapper.toCamelCase(snakeCaseResult);
    }

    /**
     * Checks tfplugin5 diagnostics for ERROR-level entries and throws if any are found.
     *
     * <p>WARNING-level diagnostics are logged but do not cause failure.</p>
     *
     * @param diagnostics the diagnostics list from a TF response
     * @param operation   the operation name for error reporting (e.g. "create", "read")
     * @throws RuntimeException if any ERROR-level diagnostic is present
     */
    protected void checkDiagnostics(List<Diagnostic> diagnostics, String operation) {
        diagnostics.stream()
                .filter(d -> d.getSeverity() == Diagnostic.Severity.WARNING)
                .forEach(d -> log.warn("Terraform {} warning for {}: {} — {}",
                        operation, tfTypeName, d.getSummary(), d.getDetail()));

        var errors = diagnostics.stream()
                .filter(d -> d.getSeverity() == Diagnostic.Severity.ERROR)
                .toList();

        if (!errors.isEmpty()) {
            var message = errors.stream()
                    .map(d -> d.getSummary() + ": " + d.getDetail())
                    .collect(Collectors.joining("; "));
            throw new RuntimeException("Terraform %s failed: %s".formatted(operation, message));
        }
    }
}
