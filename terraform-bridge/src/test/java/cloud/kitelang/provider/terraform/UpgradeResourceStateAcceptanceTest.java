package cloud.kitelang.provider.terraform;
import cloud.kitelang.tfplugin.CtyCodec;
import cloud.kitelang.tfplugin.GoPluginClient;
import cloud.kitelang.tfplugin.Tfplugin6Rpc;

import cloud.kitelang.provider.ResourceContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tfplugin6.ProviderGrpc;
import tfplugin6.Tfplugin6;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Acceptance test for kitecorp/kite-providers#5: state written under schema
 * version 1 is read by a provider release whose schema version is 2, and the
 * bridge calls {@code UpgradeResourceState} with the stored state, then uses
 * the upgraded result for the rest of the operation.
 *
 * <p>Everything on the call path is real — {@link TerraformResourceTypeHandler},
 * {@link TerraformBridgeProvider} registration, {@link Tfplugin6Rpc}, the
 * generated gRPC stubs, and protobuf marshalling over an in-process channel.
 * Only the provider <em>service</em> behind the channel is a fake: no published
 * provider binary can be forced to bump its schema version mid-test (the local
 * acceptance binaries tfcoremock and random are all at schema version 0), so a
 * fake provider whose v2 schema renamed the v1 attribute {@code chars} to
 * {@code name} plays the upgraded release.</p>
 */
@ExtendWith(MockitoExtension.class)
class UpgradeResourceStateAcceptanceTest {

    private static final String TF_TYPE = "tfupgrade_thing";
    private static final String V1_TYPE_JSON = """
            ["object", {"id": "string", "chars": "string"}]""";
    private static final String V2_TYPE_JSON = """
            ["object", {"id": "string", "name": "string"}]""";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final CtyCodec codec = new CtyCodec();
    private final FakeUpgradingProvider fakeProvider = new FakeUpgradingProvider();

    @Mock
    private GoPluginClient client;

    private Server server;
    private ManagedChannel channel;

    @BeforeEach
    void startInProcessProvider() throws IOException {
        var serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(fakeProvider)
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        when(client.rpc()).thenReturn(new Tfplugin6Rpc(ProviderGrpc.newBlockingStub(channel)));
    }

    @AfterEach
    void stopInProcessProvider() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    @DisplayName("state created under schema version 1 is upgraded when read by a version-2 provider")
    void shouldUpgradeVersion1StateWhenReadByVersion2Provider() {
        // ---- Phase 1: the version-1 provider era creates the resource ----
        // The v1 release's schema had a "chars" attribute; its handler is
        // constructed directly because that release no longer exists to init from.
        var handlerV1 = new TerraformResourceTypeHandler(TF_TYPE, "Thing", client,
                V1_TYPE_JSON, java.util.Set.of("id"), 1);

        var createInput = new LinkedHashMap<String, Object>();
        createInput.put("id", null);
        createInput.put("chars", "hello");
        var createContext = ResourceContext.<Map<String, Object>>empty();
        var created = handlerV1.create(createInput, createContext);

        var expectedV1State = new LinkedHashMap<String, Object>();
        expectedV1State.put("id", "thing-1");
        expectedV1State.put("chars", "hello");
        assertEquals(expectedV1State, created);

        // What the engine persists: the v1 state map plus enveloped private bytes
        var storedState = created;
        var storedPrivate = createContext.privateDataToReturn();
        assertArrayEquals(SchemaVersionEnvelope.wrap(1, utf8("tf-private-v1")), storedPrivate);

        // ---- Phase 2: the version-2 provider release starts up ----
        // Registration runs off the provider's real schema response, so the
        // handler's schema version comes through the full init wiring.
        var provider = new TerraformBridgeProvider("tfupgrade", client);
        provider.init();
        var handlerV2 = assertInstanceOf(TerraformResourceTypeHandler.class,
                provider.getResourceType("Thing"));

        // ---- The v2 provider reads the state the v1 provider wrote ----
        var readContext = ResourceContext.<Map<String, Object>>of(null, storedPrivate);
        var result = handlerV2.read(storedState, readContext);

        // UpgradeResourceState received the stored version and the stored
        // state in raw JSON form, dropped attribute included
        var upgradeRequest = fakeProvider.upgradeRequest.get();
        assertNotNull(upgradeRequest, "UpgradeResourceState was never called");
        assertEquals(TF_TYPE, upgradeRequest.getTypeName());
        assertEquals(1, upgradeRequest.getVersion());
        assertEquals(Map.of("id", "thing-1", "chars", "hello"),
                parseJson(upgradeRequest.getRawState().getJson().toByteArray()));

        // ReadResource consumed the UPGRADED state (chars renamed to name) and
        // the provider's own private bytes, stripped of the version envelope
        var readRequest = fakeProvider.readRequest.get();
        assertEquals(Map.of("id", "thing-1", "name", "hello"),
                codec.decode(readRequest.getCurrentState().getMsgpack().toByteArray(), V2_TYPE_JSON));
        assertEquals(ByteString.copyFromUtf8("tf-private-v1"), readRequest.getPrivate());

        // The caller sees the upgraded state — "hello" can only have travelled
        // from the stored "chars" through the provider's upgrade logic
        var expectedResult = new LinkedHashMap<String, Object>();
        expectedResult.put("id", "thing-1");
        expectedResult.put("name", "hello");
        assertEquals(expectedResult, result);

        // ...and the re-persisted private bytes record schema version 2
        assertArrayEquals(SchemaVersionEnvelope.wrap(2, utf8("tf-private-v2")),
                readContext.privateDataToReturn());
    }

    // ---------------------------------------------------------------
    // Fake tfplugin6 provider service
    // ---------------------------------------------------------------

    /**
     * A tfplugin6 provider whose current release is schema version 2: attribute
     * {@code chars} (v1) was renamed to {@code name}, and its upgrade logic
     * carries the value over. Create-path RPCs echo like a computed-value-free
     * provider; the interesting behavior is {@code UpgradeResourceState}.
     */
    private final class FakeUpgradingProvider extends ProviderGrpc.ProviderImplBase {

        final AtomicReference<Tfplugin6.UpgradeResourceState.Request> upgradeRequest = new AtomicReference<>();
        final AtomicReference<Tfplugin6.ReadResource.Request> readRequest = new AtomicReference<>();

        @Override
        public void getProviderSchema(Tfplugin6.GetProviderSchema.Request request,
                                      StreamObserver<Tfplugin6.GetProviderSchema.Response> observer) {
            var v2Schema = Tfplugin6.Schema.newBuilder()
                    .setVersion(2)
                    .setBlock(Tfplugin6.Schema.Block.newBuilder()
                            .addAttributes(Tfplugin6.Schema.Attribute.newBuilder()
                                    .setName("id")
                                    .setType(ByteString.copyFromUtf8("\"string\""))
                                    .setComputed(true))
                            .addAttributes(Tfplugin6.Schema.Attribute.newBuilder()
                                    .setName("name")
                                    .setType(ByteString.copyFromUtf8("\"string\""))
                                    .setOptional(true)))
                    .build();
            observer.onNext(Tfplugin6.GetProviderSchema.Response.newBuilder()
                    .putResourceSchemas(TF_TYPE, v2Schema)
                    .build());
            observer.onCompleted();
        }

        @Override
        public void validateResourceConfig(Tfplugin6.ValidateResourceConfig.Request request,
                                           StreamObserver<Tfplugin6.ValidateResourceConfig.Response> observer) {
            observer.onNext(Tfplugin6.ValidateResourceConfig.Response.getDefaultInstance());
            observer.onCompleted();
        }

        @Override
        public void planResourceChange(Tfplugin6.PlanResourceChange.Request request,
                                       StreamObserver<Tfplugin6.PlanResourceChange.Response> observer) {
            observer.onNext(Tfplugin6.PlanResourceChange.Response.newBuilder()
                    .setPlannedState(request.getProposedNewState())
                    .setPlannedPrivate(request.getPriorPrivate())
                    .build());
            observer.onCompleted();
        }

        @Override
        public void applyResourceChange(Tfplugin6.ApplyResourceChange.Request request,
                                        StreamObserver<Tfplugin6.ApplyResourceChange.Response> observer) {
            // The v1-era create: fill the computed id like a real provider would
            var v1State = new LinkedHashMap<String, Object>();
            v1State.put("id", "thing-1");
            v1State.put("chars", "hello");
            observer.onNext(Tfplugin6.ApplyResourceChange.Response.newBuilder()
                    .setNewState(Tfplugin6.DynamicValue.newBuilder()
                            .setMsgpack(ByteString.copyFrom(codec.encode(v1State, V1_TYPE_JSON))))
                    .setPrivate(ByteString.copyFromUtf8("tf-private-v1"))
                    .build());
            observer.onCompleted();
        }

        @Override
        public void upgradeResourceState(Tfplugin6.UpgradeResourceState.Request request,
                                         StreamObserver<Tfplugin6.UpgradeResourceState.Response> observer) {
            upgradeRequest.set(request);
            // Real upgrade logic: interpret the raw v1 JSON and rename
            // chars -> name, proving the request content feeds the result
            var v1State = parseJson(request.getRawState().getJson().toByteArray());
            var v2State = new LinkedHashMap<String, Object>();
            v2State.put("id", v1State.get("id"));
            v2State.put("name", v1State.get("chars"));
            observer.onNext(Tfplugin6.UpgradeResourceState.Response.newBuilder()
                    .setUpgradedState(Tfplugin6.DynamicValue.newBuilder()
                            .setMsgpack(ByteString.copyFrom(codec.encode(v2State, V2_TYPE_JSON))))
                    .build());
            observer.onCompleted();
        }

        @Override
        public void readResource(Tfplugin6.ReadResource.Request request,
                                 StreamObserver<Tfplugin6.ReadResource.Response> observer) {
            readRequest.set(request);
            observer.onNext(Tfplugin6.ReadResource.Response.newBuilder()
                    .setNewState(request.getCurrentState())
                    .setPrivate(ByteString.copyFromUtf8("tf-private-v2"))
                    .build());
            observer.onCompleted();
        }
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJson(byte[] json) {
        try {
            return JSON.readValue(json, Map.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
