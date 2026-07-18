package cloud.kitelang.provider.terraform;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tfplugin5.ProviderGrpc;
import tfplugin5.Tfplugin5.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TDD tests for {@link Tfplugin5Rpc} paths not already exercised through the
 * handler and bridge-provider suites (which drive a real Tfplugin5Rpc over a
 * mocked stub): the Stop RPC, the absent-provider schema mapping, and
 * attribute-path rendering of an unset selector.
 */
@ExtendWith(MockitoExtension.class)
class Tfplugin5RpcTest {

    @Mock
    private ProviderGrpc.ProviderBlockingStub stub;

    @Test
    @DisplayName("stop() should call the Stop RPC")
    void stopShouldCallStopRpc() {
        when(stub.stop(any())).thenReturn(Stop.Response.getDefaultInstance());

        new Tfplugin5Rpc(stub).stop();

        verify(stub).stop(Stop.Request.getDefaultInstance());
    }

    @Test
    @DisplayName("getProviderSchema() should map an absent provider block to a null provider schema")
    void getProviderSchemaShouldMapAbsentProviderToNull() {
        when(stub.getSchema(any())).thenReturn(GetProviderSchema.Response.getDefaultInstance());

        var schema = new Tfplugin5Rpc(stub).getProviderSchema();

        verify(stub).getSchema(GetProviderSchema.Request.getDefaultInstance());
        assertNull(schema.provider());
        assertEquals(Map.of(), schema.resourceSchemas());
        assertEquals(Map.of(), schema.dataSourceSchemas());
    }

    @Test
    @DisplayName("planResourceChange() should render an unset path selector as <?>")
    void planShouldRenderUnsetSelector() {
        when(stub.planResourceChange(any())).thenReturn(PlanResourceChange.Response.newBuilder()
                .addRequiresReplace(AttributePath.newBuilder()
                        .addSteps(AttributePath.Step.newBuilder().setAttributeName("ami"))
                        .addSteps(AttributePath.Step.getDefaultInstance()))
                .build());

        var plan = new Tfplugin5Rpc(stub).planResourceChange(
                "aws_instance", new byte[]{1}, new byte[]{2}, new byte[]{3}, new byte[0]);

        assertEquals(List.of("ami<?>"),
                plan.requiresReplace().stream().map(TfAttributePath::render).toList());
    }
}
