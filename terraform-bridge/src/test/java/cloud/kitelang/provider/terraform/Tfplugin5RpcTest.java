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
    @DisplayName("validateProviderConfig() should call PrepareProviderConfig and return the prepared config")
    void validateProviderConfigShouldCallPrepareProviderConfig() {
        // tfplugin5's legacy-SDK providers fill defaults in PrepareProviderConfig;
        // the prepared config, not the original, must be what Configure receives.
        var prepared = new byte[]{9, 9};
        when(stub.prepareProviderConfig(any())).thenReturn(PrepareProviderConfig.Response.newBuilder()
                .setPreparedConfig(DynamicValue.newBuilder()
                        .setMsgpack(ByteString.copyFrom(prepared)))
                .build());

        var validation = new Tfplugin5Rpc(stub).validateProviderConfig(new byte[]{1});

        assertArrayEquals(prepared, validation.preparedConfig());
        assertEquals(List.of(), validation.diagnostics());
        var captor = org.mockito.ArgumentCaptor.forClass(PrepareProviderConfig.Request.class);
        verify(stub).prepareProviderConfig(captor.capture());
        assertEquals(ByteString.copyFrom(new byte[]{1}), captor.getValue().getConfig().getMsgpack());
    }

    @Test
    @DisplayName("validateProviderConfig() should fall back to the input config when prepared_config is empty")
    void validateProviderConfigShouldFallBackToInputConfig() {
        when(stub.prepareProviderConfig(any()))
                .thenReturn(PrepareProviderConfig.Response.getDefaultInstance());

        var validation = new Tfplugin5Rpc(stub).validateProviderConfig(new byte[]{7});

        assertArrayEquals(new byte[]{7}, validation.preparedConfig());
    }

    @Test
    @DisplayName("validateProviderConfig() should map an ERROR diagnostic with its attribute path")
    void validateProviderConfigShouldMapDiagnosticAttributePath() {
        when(stub.prepareProviderConfig(any())).thenReturn(PrepareProviderConfig.Response.newBuilder()
                .addDiagnostics(Diagnostic.newBuilder()
                        .setSeverity(Diagnostic.Severity.ERROR)
                        .setSummary("Invalid region")
                        .setDetail("region 'mars' is not valid")
                        .setAttribute(AttributePath.newBuilder()
                                .addSteps(AttributePath.Step.newBuilder().setAttributeName("region"))))
                .build());

        var validation = new Tfplugin5Rpc(stub).validateProviderConfig(new byte[]{1});

        assertEquals(1, validation.diagnostics().size());
        var diagnostic = validation.diagnostics().get(0);
        assertEquals(TfDiagnostic.Severity.ERROR, diagnostic.severity());
        assertEquals("Invalid region", diagnostic.summary());
        assertEquals("region", diagnostic.attributePath().render());
    }

    @Test
    @DisplayName("configure() should map a diagnostic without an attribute to a null attribute path")
    void configureShouldMapAbsentAttributeToNullPath() {
        when(stub.configure(any())).thenReturn(Configure.Response.newBuilder()
                .addDiagnostics(Diagnostic.newBuilder()
                        .setSeverity(Diagnostic.Severity.ERROR)
                        .setSummary("Boom")
                        .setDetail("it broke"))
                .build());

        var diagnostics = new Tfplugin5Rpc(stub).configure(new byte[]{1});

        assertEquals(List.of(new TfDiagnostic(TfDiagnostic.Severity.ERROR, "Boom", "it broke")),
                diagnostics);
        assertNull(diagnostics.get(0).attributePath());
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
