package cloud.kitelang.provider.terraform;

import cloud.kitelang.provider.ResourceContext;
import cloud.kitelang.tfplugin.CtyCodec;
import cloud.kitelang.tfplugin.GoPluginClient;
import cloud.kitelang.tfplugin.Tfplugin6Rpc;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tfplugin6.ProviderGrpc;
import tfplugin6.Tfplugin6.ApplyResourceChange;
import tfplugin6.Tfplugin6.DynamicValue;
import tfplugin6.Tfplugin6.PlanResourceChange;
import tfplugin6.Tfplugin6.ValidateResourceConfig;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link TerraformResourceTypeHandler} drives a resource create
 * end to end through the <em>tfplugin6</em> RPCs — the version-agnostic seam
 * where the bridge handler meets the protocol library's
 * {@link Tfplugin6Rpc}. The pure {@code Tfplugin6Rpc} mapping tests live in the
 * {@code tfplugin-jvm} library; this one stays with the bridge because it
 * exercises Kite-side pieces the library has no knowledge of: the handler,
 * {@link ResourceContext}, and the bridge's {@link SchemaVersionEnvelope} state
 * wrapping.
 */
@ExtendWith(MockitoExtension.class)
class TerraformResourceTypeHandlerTfplugin6Test {

    private static final String TYPE_NAME = "aws_instance";

    private static final String SCHEMA_TYPE_JSON = """
            ["object", {"ami": "string", "instance_type": "string"}]""";

    @Mock
    private ProviderGrpc.ProviderBlockingStub stub;

    @Mock
    private GoPluginClient client;

    private static DynamicValue msgpack(byte[] bytes) {
        return DynamicValue.newBuilder().setMsgpack(ByteString.copyFrom(bytes)).build();
    }

    @Test
    @DisplayName("create() should run validate + plan + apply through the tfplugin6 RPCs")
    void createShouldRunThroughTfplugin6Rpcs() {
        when(client.rpc()).thenReturn(new Tfplugin6Rpc(stub));
        var codec = new CtyCodec();
        var handler = new TerraformResourceTypeHandler(
                TYPE_NAME, "Instance", client, SCHEMA_TYPE_JSON, Set.of(), 0);

        when(stub.validateResourceConfig(any()))
                .thenReturn(ValidateResourceConfig.Response.getDefaultInstance());
        when(stub.planResourceChange(any())).thenAnswer(invocation -> {
            var request = (PlanResourceChange.Request) invocation.getArgument(0);
            return PlanResourceChange.Response.newBuilder()
                    .setPlannedState(request.getProposedNewState())
                    .build();
        });
        var snakeState = new LinkedHashMap<String, Object>();
        snakeState.put("ami", "ami-12345");
        snakeState.put("instance_type", "t2.micro");
        when(stub.applyResourceChange(any())).thenReturn(ApplyResourceChange.Response.newBuilder()
                .setNewState(msgpack(codec.encode(snakeState, SCHEMA_TYPE_JSON)))
                .setPrivate(ByteString.copyFromUtf8("proto6-private"))
                .build());

        var input = new LinkedHashMap<String, Object>();
        input.put("ami", "ami-12345");
        input.put("instanceType", "t2.micro");
        var context = ResourceContext.<Map<String, Object>>empty();
        var result = handler.create(input, context);

        var inOrder = inOrder(stub);
        inOrder.verify(stub).validateResourceConfig(any(ValidateResourceConfig.Request.class));
        inOrder.verify(stub).planResourceChange(any(PlanResourceChange.Request.class));
        inOrder.verify(stub).applyResourceChange(any(ApplyResourceChange.Request.class));

        assertEquals("ami-12345", result.get("ami"));
        assertEquals("t2.micro", result.get("instanceType"));
        assertArrayEquals(
                SchemaVersionEnvelope.wrap(0, "proto6-private".getBytes(StandardCharsets.UTF_8)),
                context.privateDataToReturn());
    }
}
