package cloud.kitelang.provider.terraform;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import tfplugin5.Tfplugin5.ApplyResourceChange;
import tfplugin5.Tfplugin5.DynamicValue;
import tfplugin5.Tfplugin5.ReadResource;

import java.util.Map;

/**
 * Bridges Kite CRUD operations to Terraform's tfplugin5 gRPC protocol.
 *
 * <p>One instance per Terraform resource type. Translates between Kite's camelCase
 * {@code Map<String, Object>} property bags and Terraform's snake_case cty
 * msgpack-encoded {@link DynamicValue} messages.</p>
 *
 * <h3>CRUD mapping to Terraform RPCs</h3>
 * <ul>
 *   <li>{@link #create} &rarr; {@code ApplyResourceChange} (null prior state)</li>
 *   <li>{@link #read}   &rarr; {@code ReadResource}</li>
 *   <li>{@link #update} &rarr; {@code ReadResource} + {@code ApplyResourceChange}</li>
 *   <li>{@link #delete} &rarr; {@code ApplyResourceChange} (null planned state)</li>
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
     * Creates a handler for a single Terraform resource type.
     *
     * @param tfTypeName     the original TF resource type name (e.g. {@code "aws_instance"})
     * @param kiteTypeName   the converted Kite type name (e.g. {@code "Instance"})
     * @param client         the go-plugin gRPC client for making tfplugin5 calls
     * @param schemaTypeJson JSON-encoded cty object type for this resource,
     *                       e.g. {@code ["object", {"ami": "string", "instance_type": "string"}]}
     */
    public TerraformResourceTypeHandler(String tfTypeName, String kiteTypeName,
                                        GoPluginClient client, String schemaTypeJson) {
        super(tfTypeName, kiteTypeName, client, schemaTypeJson);
    }

    /**
     * Creates a new resource via {@code ApplyResourceChange} with null prior state.
     *
     * @param properties camelCase property map from Kite
     * @return the created resource state in camelCase
     */
    @Override
    public Map<String, Object> create(Map<String, Object> properties) {
        var snakeProps = TerraformPropertyMapper.toSnakeCase(properties);
        var encodedState = encodeToDynamicValue(snakeProps);

        var request = ApplyResourceChange.Request.newBuilder()
                .setTypeName(tfTypeName)
                .setPriorState(nullDynamicValue())
                .setPlannedState(encodedState)
                .setConfig(encodedState)
                .setPlannedPrivate(privateData)
                .build();

        log.debug("create {} — sending ApplyResourceChange", tfTypeName);
        var response = client.getStub().applyResourceChange(request);
        checkDiagnostics(response.getDiagnosticsList(), "create");

        privateData = response.getPrivate();
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
        return decodeResponse(response.getNewState());
    }

    /**
     * Updates a resource by first reading its current state, then calling
     * {@code ApplyResourceChange} with both prior and planned states.
     *
     * @param properties the desired camelCase property map
     * @return the updated resource state in camelCase
     */
    @Override
    public Map<String, Object> update(Map<String, Object> properties) {
        // Read current state to use as prior
        var snakeProps = TerraformPropertyMapper.toSnakeCase(properties);
        var currentState = encodeToDynamicValue(snakeProps);

        var readRequest = ReadResource.Request.newBuilder()
                .setTypeName(tfTypeName)
                .setCurrentState(currentState)
                .setPrivate(privateData)
                .build();

        log.debug("update {} — reading prior state via ReadResource", tfTypeName);
        var readResponse = client.getStub().readResource(readRequest);
        checkDiagnostics(readResponse.getDiagnosticsList(), "update (read prior)");
        privateData = readResponse.getPrivate();

        // Apply the change with prior + planned
        var plannedState = encodeToDynamicValue(snakeProps);

        var applyRequest = ApplyResourceChange.Request.newBuilder()
                .setTypeName(tfTypeName)
                .setPriorState(readResponse.getNewState())
                .setPlannedState(plannedState)
                .setConfig(plannedState)
                .setPlannedPrivate(privateData)
                .build();

        log.debug("update {} — sending ApplyResourceChange", tfTypeName);
        var applyResponse = client.getStub().applyResourceChange(applyRequest);
        checkDiagnostics(applyResponse.getDiagnosticsList(), "update");

        privateData = applyResponse.getPrivate();
        return decodeResponse(applyResponse.getNewState());
    }

    /**
     * Deletes a resource via {@code ApplyResourceChange} with null planned state.
     *
     * @param properties camelCase property map identifying the resource to delete
     * @return {@code true} always (TF indicates failure via diagnostics/exceptions)
     */
    @Override
    public boolean delete(Map<String, Object> properties) {
        var snakeProps = TerraformPropertyMapper.toSnakeCase(properties);
        var encodedState = encodeToDynamicValue(snakeProps);

        var request = ApplyResourceChange.Request.newBuilder()
                .setTypeName(tfTypeName)
                .setPriorState(encodedState)
                .setPlannedState(nullDynamicValue())
                .setConfig(nullDynamicValue())
                .setPlannedPrivate(privateData)
                .build();

        log.debug("delete {} — sending ApplyResourceChange with null planned state", tfTypeName);
        var response = client.getStub().applyResourceChange(request);
        checkDiagnostics(response.getDiagnosticsList(), "delete");

        privateData = response.getPrivate();
        return true;
    }
}
