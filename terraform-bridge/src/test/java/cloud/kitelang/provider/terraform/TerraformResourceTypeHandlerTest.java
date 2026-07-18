package cloud.kitelang.provider.terraform;

import cloud.kitelang.provider.ResourceContext;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tfplugin5.ProviderGrpc;
import tfplugin5.Tfplugin5.*;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TDD tests for {@link TerraformResourceTypeHandler} and {@link TerraformDataSourceHandler}.
 *
 * <p>Mocks the GoPluginClient and its gRPC stub so we can verify correct protobuf
 * message construction and response decoding without a running provider process.</p>
 */
@ExtendWith(MockitoExtension.class)
class TerraformResourceTypeHandlerTest {

    private static final String TF_TYPE_NAME = "aws_instance";
    private static final String KITE_TYPE_NAME = "Instance";
    private static final String SCHEMA_TYPE_JSON = """
            ["object", {"ami": "string", "instance_type": "string"}]""";

    @Mock
    private GoPluginClient client;

    @Mock
    private ProviderGrpc.ProviderBlockingStub stub;

    private CtyCodec codec;
    private TerraformResourceTypeHandler handler;

    @BeforeEach
    void setUp() {
        // A real Tfplugin5Rpc over the mocked stub: the tests keep verifying the
        // exact tfplugin5 protobuf requests, now including the adapter's mapping
        lenient().when(client.rpc()).thenReturn(new Tfplugin5Rpc(stub));
        codec = new CtyCodec();
        handler = new TerraformResourceTypeHandler(TF_TYPE_NAME, KITE_TYPE_NAME, client, SCHEMA_TYPE_JSON,
                Set.of());

        // Every apply is now preceded by validate + plan (mirrors Terraform core).
        // Default happy-path stubs: validation passes; the plan echoes the proposed
        // state and relays the prior private bytes, like a provider with no
        // computed attributes. Individual tests re-stub to exercise failures,
        // computed values, and requiresReplace.
        lenient().when(stub.validateResourceTypeConfig(any()))
                .thenReturn(ValidateResourceTypeConfig.Response.getDefaultInstance());
        lenient().when(stub.planResourceChange(any())).thenAnswer(invocation -> {
            var request = (PlanResourceChange.Request) invocation.getArgument(0);
            return PlanResourceChange.Response.newBuilder()
                    .setPlannedState(request.getProposedNewState())
                    .setPlannedPrivate(request.getPriorPrivate())
                    .build();
        });
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /** Single msgpack nil byte (0xc0) — the encoding of a null cty value. */
    private static final ByteString MSGPACK_NIL = ByteString.copyFrom(new byte[]{(byte) 0xc0});

    /**
     * True when the DynamicValue is the msgpack nil encoding of a null cty value.
     */
    private static boolean isNil(DynamicValue value) {
        return MSGPACK_NIL.equals(value.getMsgpack());
    }

    /**
     * Builds a DynamicValue from a snake_case property map using the test schema.
     */
    private DynamicValue encodeToDynamicValue(Map<String, Object> snakeCaseProps) {
        var bytes = codec.encode(snakeCaseProps, SCHEMA_TYPE_JSON);
        return DynamicValue.newBuilder().setMsgpack(ByteString.copyFrom(bytes)).build();
    }

    /**
     * Creates a camelCase input map (as Kite would pass to handlers).
     */
    private Map<String, Object> camelCaseProps(String ami, String instanceType) {
        var map = new LinkedHashMap<String, Object>();
        map.put("ami", ami);
        map.put("instanceType", instanceType);
        return map;
    }

    /**
     * Creates a snake_case map (as Terraform expects).
     */
    private Map<String, Object> snakeCaseProps(String ami, String instanceType) {
        var map = new LinkedHashMap<String, Object>();
        map.put("ami", ami);
        map.put("instance_type", instanceType);
        return map;
    }

    // ---------------------------------------------------------------
    // 1. create()
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("should call ApplyResourceChange with null prior state and correct planned state")
        void shouldCallApplyResourceChangeWithNullPriorState() {
            // Given
            var input = camelCaseProps("ami-12345", "t2.micro");
            var responseState = encodeToDynamicValue(snakeCaseProps("ami-12345", "t2.micro"));
            var response = ApplyResourceChange.Response.newBuilder()
                    .setNewState(responseState)
                    .build();
            when(stub.applyResourceChange(any())).thenReturn(response);

            // When
            var result = handler.create(input);

            // Then
            var captor = ArgumentCaptor.forClass(ApplyResourceChange.Request.class);
            verify(stub).applyResourceChange(captor.capture());
            var request = captor.getValue();

            assertEquals(TF_TYPE_NAME, request.getTypeName());
            // Prior state should be msgpack nil (null cty value for create)
            assertTrue(request.getPriorState().getMsgpack().size() > 0,
                    "Prior state should contain msgpack nil, not empty bytes");
            // Planned state should be set
            assertNotEquals(DynamicValue.getDefaultInstance(), request.getPlannedState());
            // Config should be set
            assertNotEquals(DynamicValue.getDefaultInstance(), request.getConfig());
            // Result should have camelCase keys
            assertEquals("ami-12345", result.get("ami"));
            assertEquals("t2.micro", result.get("instanceType"));
        }

        @Test
        @DisplayName("should return private data from create response through the context")
        void shouldReturnPrivateDataThroughContext() {
            // Given
            var input = camelCaseProps("ami-12345", "t2.micro");
            var privateBytes = ByteString.copyFromUtf8("opaque-provider-state");
            var responseState = encodeToDynamicValue(snakeCaseProps("ami-12345", "t2.micro"));
            var response = ApplyResourceChange.Response.newBuilder()
                    .setNewState(responseState)
                    .setPrivate(privateBytes)
                    .build();
            when(stub.applyResourceChange(any())).thenReturn(response);

            // When — the context is the only channel for private bytes now;
            // the handler holds no in-memory copy across operations
            var createContext = ResourceContext.<Map<String, Object>>empty();
            handler.create(input, createContext);

            // Then — the engine persists these; supplying them on a later read
            // (fresh context, as after a process restart) relays them to TF
            assertArrayEquals("opaque-provider-state".getBytes(StandardCharsets.UTF_8),
                    createContext.privateDataToReturn());

            var readResponse = ReadResource.Response.newBuilder()
                    .setNewState(encodeToDynamicValue(snakeCaseProps("ami-12345", "t2.micro")))
                    .setPrivate(privateBytes)
                    .build();
            when(stub.readResource(any())).thenReturn(readResponse);

            handler.read(input, ResourceContext.of(null, createContext.privateDataToReturn()));

            var readCaptor = ArgumentCaptor.forClass(ReadResource.Request.class);
            verify(stub).readResource(readCaptor.capture());
            assertEquals(privateBytes, readCaptor.getValue().getPrivate());
        }
    }

    // ---------------------------------------------------------------
    // 1b. Validate + plan wiring (Terraform core always validates and
    //     plans before applying; the bridge must do the same)
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("Validate + plan before apply")
    class ValidateAndPlanWiring {

        @Test
        @DisplayName("create() should call ValidateResourceTypeConfig and PlanResourceChange before ApplyResourceChange")
        void createShouldValidateAndPlanBeforeApply() {
            // Given — the plan fills in a computed value the config did not have
            var input = camelCaseProps("ami-12345", null);
            var plannedState = encodeToDynamicValue(snakeCaseProps("ami-12345", "t2.micro"));
            var plannedPrivate = ByteString.copyFromUtf8("plan-private");
            var planResponse = PlanResourceChange.Response.newBuilder()
                    .setPlannedState(plannedState)
                    .setPlannedPrivate(plannedPrivate)
                    .build();
            doReturn(planResponse).when(stub).planResourceChange(any());

            var applyResponse = ApplyResourceChange.Response.newBuilder()
                    .setNewState(encodeToDynamicValue(snakeCaseProps("ami-12345", "t2.micro")))
                    .build();
            when(stub.applyResourceChange(any())).thenReturn(applyResponse);

            // When
            var result = handler.create(input);

            // Then — RPC order matches Terraform core: validate, plan, apply
            var inOrder = inOrder(stub);
            var validateCaptor = ArgumentCaptor.forClass(ValidateResourceTypeConfig.Request.class);
            var planCaptor = ArgumentCaptor.forClass(PlanResourceChange.Request.class);
            var applyCaptor = ArgumentCaptor.forClass(ApplyResourceChange.Request.class);
            inOrder.verify(stub).validateResourceTypeConfig(validateCaptor.capture());
            inOrder.verify(stub).planResourceChange(planCaptor.capture());
            inOrder.verify(stub).applyResourceChange(applyCaptor.capture());

            var expectedConfig = encodeToDynamicValue(snakeCaseProps("ami-12345", null));
            assertEquals(TF_TYPE_NAME, validateCaptor.getValue().getTypeName());
            assertEquals(expectedConfig, validateCaptor.getValue().getConfig());

            var planRequest = planCaptor.getValue();
            assertEquals(TF_TYPE_NAME, planRequest.getTypeName());
            assertEquals(MSGPACK_NIL, planRequest.getPriorState().getMsgpack());
            assertEquals(expectedConfig, planRequest.getProposedNewState());
            assertEquals(expectedConfig, planRequest.getConfig());

            // Apply must use the plan's planned state/private, not the raw config
            var applyRequest = applyCaptor.getValue();
            assertEquals(plannedState, applyRequest.getPlannedState());
            assertEquals(plannedPrivate, applyRequest.getPlannedPrivate());
            assertEquals(expectedConfig, applyRequest.getConfig());
            assertEquals(MSGPACK_NIL, applyRequest.getPriorState().getMsgpack());

            assertEquals("ami-12345", result.get("ami"));
            assertEquals("t2.micro", result.get("instanceType"));
        }

        @Test
        @DisplayName("create() should throw on validation errors without calling plan or apply")
        void createShouldFailFastOnValidationError() {
            // Given
            var input = camelCaseProps("ami-bad", "t2.micro");
            var validateResponse = ValidateResourceTypeConfig.Response.newBuilder()
                    .addDiagnostics(Diagnostic.newBuilder()
                            .setSeverity(Diagnostic.Severity.ERROR)
                            .setSummary("InvalidConfig")
                            .setDetail("ami is malformed")
                            .build())
                    .build();
            when(stub.validateResourceTypeConfig(any())).thenReturn(validateResponse);

            // When/Then
            var exception = assertThrows(RuntimeException.class, () -> handler.create(input));
            assertTrue(exception.getMessage().contains("InvalidConfig"));
            assertTrue(exception.getMessage().contains("ami is malformed"));
            verify(stub, never()).planResourceChange(any());
            verify(stub, never()).applyResourceChange(any());
        }

        @Test
        @DisplayName("create() should throw on plan errors without calling apply")
        void createShouldFailFastOnPlanError() {
            // Given
            var input = camelCaseProps("ami-12345", "t2.micro");
            var planResponse = PlanResourceChange.Response.newBuilder()
                    .addDiagnostics(Diagnostic.newBuilder()
                            .setSeverity(Diagnostic.Severity.ERROR)
                            .setSummary("PlanFailed")
                            .setDetail("conflicting attributes")
                            .build())
                    .build();
            doReturn(planResponse).when(stub).planResourceChange(any());

            // When/Then
            var exception = assertThrows(RuntimeException.class, () -> handler.create(input));
            assertTrue(exception.getMessage().contains("PlanFailed"));
            assertTrue(exception.getMessage().contains("conflicting attributes"));
            verify(stub, never()).applyResourceChange(any());
        }

        @Test
        @DisplayName("update() should apply in place with the plan's planned state and private when no replacement is required")
        void updateShouldApplyInPlaceWithPlannedState() {
            // Given — the engine supplies the stored prior state and private
            // bytes; the bridge must not reconstruct them via ReadResource
            var input = camelCaseProps("ami-99999", "t3.large");
            var storedPrior = camelCaseProps("ami-12345", "t2.micro");
            var storedPrivate = "stored-private".getBytes(StandardCharsets.UTF_8);
            var priorState = encodeToDynamicValue(snakeCaseProps("ami-12345", "t2.micro"));

            var plannedState = encodeToDynamicValue(snakeCaseProps("ami-99999", "t3.large"));
            var plannedPrivate = ByteString.copyFromUtf8("update-plan-private");
            var planResponse = PlanResourceChange.Response.newBuilder()
                    .setPlannedState(plannedState)
                    .setPlannedPrivate(plannedPrivate)
                    .build();
            doReturn(planResponse).when(stub).planResourceChange(any());

            var applyResponse = ApplyResourceChange.Response.newBuilder()
                    .setNewState(plannedState)
                    .setPrivate(ByteString.copyFromUtf8("post-update-private"))
                    .build();
            when(stub.applyResourceChange(any())).thenReturn(applyResponse);

            // When
            var context = ResourceContext.of(storedPrior, storedPrivate);
            var result = handler.update(input, context);

            // Then — no ReadResource; one validate, one plan, one apply
            verify(stub, never()).readResource(any());
            verify(stub).validateResourceTypeConfig(any());
            var planCaptor = ArgumentCaptor.forClass(PlanResourceChange.Request.class);
            verify(stub).planResourceChange(planCaptor.capture());
            assertEquals(priorState, planCaptor.getValue().getPriorState());
            assertEquals(ByteString.copyFromUtf8("stored-private"), planCaptor.getValue().getPriorPrivate());

            var applyCaptor = ArgumentCaptor.forClass(ApplyResourceChange.Request.class);
            verify(stub).applyResourceChange(applyCaptor.capture());
            var applyRequest = applyCaptor.getValue();

            assertEquals(priorState, applyRequest.getPriorState());
            assertEquals(plannedState, applyRequest.getPlannedState());
            assertEquals(plannedPrivate, applyRequest.getPlannedPrivate());
            assertEquals("ami-99999", result.get("ami"));
            assertEquals("t3.large", result.get("instanceType"));
            assertArrayEquals("post-update-private".getBytes(StandardCharsets.UTF_8),
                    context.privateDataToReturn());
        }

        @Test
        @DisplayName("delete() should validate and plan the destroy with stored private bytes before applying")
        void deleteShouldValidateAndPlanDestroy() {
            // Given — the engine passes the stored prior state as the property
            // map and the stored private bytes through the context
            var input = camelCaseProps("ami-12345", "t2.micro");
            var storedPrivate = "stored-private".getBytes(StandardCharsets.UTF_8);
            var plannedPrivate = ByteString.copyFromUtf8("destroy-plan-private");
            var planResponse = PlanResourceChange.Response.newBuilder()
                    .setPlannedState(DynamicValue.newBuilder().setMsgpack(MSGPACK_NIL).build())
                    .setPlannedPrivate(plannedPrivate)
                    .build();
            doReturn(planResponse).when(stub).planResourceChange(any());
            var applyResponse = ApplyResourceChange.Response.newBuilder()
                    .setNewState(DynamicValue.newBuilder().setMsgpack(MSGPACK_NIL).build())
                    .build();
            when(stub.applyResourceChange(any())).thenReturn(applyResponse);

            // When
            var result = handler.delete(input, ResourceContext.of(null, storedPrivate));

            // Then
            assertTrue(result);
            var inOrder = inOrder(stub);
            var planCaptor = ArgumentCaptor.forClass(PlanResourceChange.Request.class);
            var applyCaptor = ArgumentCaptor.forClass(ApplyResourceChange.Request.class);
            inOrder.verify(stub).validateResourceTypeConfig(any());
            inOrder.verify(stub).planResourceChange(planCaptor.capture());
            inOrder.verify(stub).applyResourceChange(applyCaptor.capture());

            // Destroy plan: prior = stored state + stored private, proposed/config = nil
            var expectedPrior = encodeToDynamicValue(snakeCaseProps("ami-12345", "t2.micro"));
            var planRequest = planCaptor.getValue();
            assertEquals(expectedPrior, planRequest.getPriorState());
            assertEquals(ByteString.copyFromUtf8("stored-private"), planRequest.getPriorPrivate());
            assertEquals(MSGPACK_NIL, planRequest.getProposedNewState().getMsgpack());
            assertEquals(MSGPACK_NIL, planRequest.getConfig().getMsgpack());

            // Apply relays the destroy plan's planned state and private bytes
            var applyRequest = applyCaptor.getValue();
            assertEquals(MSGPACK_NIL, applyRequest.getPlannedState().getMsgpack());
            assertEquals(plannedPrivate, applyRequest.getPlannedPrivate());
        }

        @Test
        @DisplayName("should null read-only attributes in the config but keep them in the proposed state")
        void shouldNullReadOnlyAttributesInConfig() {
            // Given — instance_type plays the role of a computed-only attribute;
            // providers reject configs that set read-only attributes, so the
            // bridge must strip them exactly like Terraform core does.
            var readOnlyHandler = new TerraformResourceTypeHandler(
                    TF_TYPE_NAME, KITE_TYPE_NAME, client, SCHEMA_TYPE_JSON, Set.of("instance_type"));
            var input = camelCaseProps("ami-new", "computed-value");

            when(stub.applyResourceChange(any())).thenReturn(ApplyResourceChange.Response.newBuilder()
                    .setNewState(encodeToDynamicValue(snakeCaseProps("ami-new", "computed-value")))
                    .build());

            // When — prior state comes from the engine, not a ReadResource round-trip
            readOnlyHandler.update(input,
                    ResourceContext.of(camelCaseProps("ami-old", "computed-value"), null));

            // Then — the config sent to validate and plan nulls the read-only attribute
            var validateCaptor = ArgumentCaptor.forClass(ValidateResourceTypeConfig.Request.class);
            verify(stub).validateResourceTypeConfig(validateCaptor.capture());
            var validatedConfig = codec.decode(
                    validateCaptor.getValue().getConfig().getMsgpack().toByteArray(), SCHEMA_TYPE_JSON);
            assertEquals("ami-new", validatedConfig.get("ami"));
            assertNull(validatedConfig.get("instance_type"));

            var planCaptor = ArgumentCaptor.forClass(PlanResourceChange.Request.class);
            verify(stub).planResourceChange(planCaptor.capture());
            var plannedConfig = codec.decode(
                    planCaptor.getValue().getConfig().getMsgpack().toByteArray(), SCHEMA_TYPE_JSON);
            assertNull(plannedConfig.get("instance_type"));

            // ...but the proposed state keeps the computed value (Terraform core
            // merges prior computed values into the proposal)
            var proposedState = codec.decode(
                    planCaptor.getValue().getProposedNewState().getMsgpack().toByteArray(), SCHEMA_TYPE_JSON);
            assertEquals("computed-value", proposedState.get("instance_type"));
        }
    }

    // ---------------------------------------------------------------
    // 1c. requiresReplace — destroy-and-recreate inside update()
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("requiresReplace destroy-and-recreate")
    class RequiresReplace {

        @Test
        @DisplayName("update() should destroy then recreate when the plan flags requiresReplace")
        void updateShouldDestroyAndRecreateOnRequiresReplace() {
            // Given — engine-stored prior state differs from desired on an
            // immutable attribute; no ReadResource reconstruction happens
            var input = camelCaseProps("ami-new", "t2.micro");
            var storedPrior = camelCaseProps("ami-old", "t2.micro");
            var priorState = encodeToDynamicValue(snakeCaseProps("ami-old", "t2.micro"));

            var desiredState = encodeToDynamicValue(snakeCaseProps("ami-new", "t2.micro"));
            var createPlannedPrivate = ByteString.copyFromUtf8("recreate-private");

            // The update plan flags "ami" as forcing replacement. The subsequent
            // destroy plan (proposed = nil) and create plan (prior = nil) are told
            // apart by request shape, not call order, so the stub stays robust.
            doAnswer(invocation -> {
                var request = (PlanResourceChange.Request) invocation.getArgument(0);
                if (isNil(request.getProposedNewState())) {
                    return PlanResourceChange.Response.newBuilder()
                            .setPlannedState(DynamicValue.newBuilder().setMsgpack(MSGPACK_NIL).build())
                            .build();
                }
                if (isNil(request.getPriorState())) {
                    return PlanResourceChange.Response.newBuilder()
                            .setPlannedState(desiredState)
                            .setPlannedPrivate(createPlannedPrivate)
                            .build();
                }
                return PlanResourceChange.Response.newBuilder()
                        .setPlannedState(desiredState)
                        .addRequiresReplace(AttributePath.newBuilder()
                                .addSteps(AttributePath.Step.newBuilder().setAttributeName("ami")))
                        .build();
            }).when(stub).planResourceChange(any());

            when(stub.applyResourceChange(any())).thenAnswer(invocation -> {
                var request = (ApplyResourceChange.Request) invocation.getArgument(0);
                if (isNil(request.getPlannedState())) {
                    // Destroy apply — resource is gone
                    return ApplyResourceChange.Response.newBuilder()
                            .setNewState(DynamicValue.newBuilder().setMsgpack(MSGPACK_NIL).build())
                            .build();
                }
                return ApplyResourceChange.Response.newBuilder()
                        .setNewState(desiredState)
                        .build();
            });

            // When — prior state and private bytes come from the engine
            var context = ResourceContext.of(storedPrior,
                    "stored-private".getBytes(StandardCharsets.UTF_8));
            var result = handler.update(input, context);

            // Then — no ReadResource; plan sequence: update plan, destroy plan, create plan
            verify(stub, never()).readResource(any());
            var planCaptor = ArgumentCaptor.forClass(PlanResourceChange.Request.class);
            verify(stub, times(3)).planResourceChange(planCaptor.capture());
            // Update and destroy plans carry the stored private bytes; the
            // recreate plan starts from scratch with empty private
            assertEquals(ByteString.copyFromUtf8("stored-private"),
                    planCaptor.getAllValues().get(0).getPriorPrivate());
            assertEquals(ByteString.copyFromUtf8("stored-private"),
                    planCaptor.getAllValues().get(1).getPriorPrivate());
            assertEquals(ByteString.EMPTY, planCaptor.getAllValues().get(2).getPriorPrivate());

            var applyCaptor = ArgumentCaptor.forClass(ApplyResourceChange.Request.class);
            verify(stub, times(2)).applyResourceChange(applyCaptor.capture());

            // First apply destroys the old resource: prior = engine-stored state, planned = nil
            var destroyRequest = applyCaptor.getAllValues().get(0);
            assertEquals(priorState, destroyRequest.getPriorState());
            assertEquals(MSGPACK_NIL, destroyRequest.getPlannedState().getMsgpack());
            assertEquals(MSGPACK_NIL, destroyRequest.getConfig().getMsgpack());

            // Second apply creates the replacement: prior = nil, planned from create plan
            var createRequest = applyCaptor.getAllValues().get(1);
            assertEquals(MSGPACK_NIL, createRequest.getPriorState().getMsgpack());
            assertEquals(desiredState, createRequest.getPlannedState());
            assertEquals(createPlannedPrivate, createRequest.getPlannedPrivate());

            // Result reflects the recreated resource
            assertEquals("ami-new", result.get("ami"));
            assertEquals("t2.micro", result.get("instanceType"));
        }
    }

    // ---------------------------------------------------------------
    // 1d. plan() — diff preview with unknown ("known after apply") values
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("plan() diff preview")
    class PlanPreview {

        @Test
        @DisplayName("should return the planned state with UNKNOWN sentinels for computed values")
        void planShouldSurfaceUnknownValues() {
            // Given — the provider marks the computed attribute as unknown at plan time
            var plannedSnake = new LinkedHashMap<String, Object>();
            plannedSnake.put("ami", "ami-12345");
            plannedSnake.put("instance_type", CtyCodec.UNKNOWN);
            var planResponse = PlanResourceChange.Response.newBuilder()
                    .setPlannedState(encodeToDynamicValue(plannedSnake))
                    .build();
            doReturn(planResponse).when(stub).planResourceChange(any());

            // When — no prior state: this is a create preview
            var planned = handler.plan(null, camelCaseProps("ami-12345", null));

            // Then — unknown decodes to the sentinel, known values pass through
            assertEquals("ami-12345", planned.get("ami"));
            assertSame(CtyCodec.UNKNOWN, planned.get("instanceType"));
            var planCaptor = ArgumentCaptor.forClass(PlanResourceChange.Request.class);
            verify(stub).planResourceChange(planCaptor.capture());
            assertEquals(MSGPACK_NIL, planCaptor.getValue().getPriorState().getMsgpack());
            // Preview never applies
            verify(stub, never()).applyResourceChange(any());
        }

        @Test
        @DisplayName("should encode the prior state into the plan request for update previews")
        void planShouldEncodePriorStateForUpdates() {
            // Given
            var planResponse = PlanResourceChange.Response.newBuilder()
                    .setPlannedState(encodeToDynamicValue(snakeCaseProps("ami-new", "t2.micro")))
                    .build();
            doReturn(planResponse).when(stub).planResourceChange(any());

            // When
            var planned = handler.plan(
                    camelCaseProps("ami-old", "t2.micro"),
                    camelCaseProps("ami-new", "t2.micro"));

            // Then
            var planCaptor = ArgumentCaptor.forClass(PlanResourceChange.Request.class);
            verify(stub).planResourceChange(planCaptor.capture());
            var expectedPrior = encodeToDynamicValue(snakeCaseProps("ami-old", "t2.micro"));
            assertEquals(expectedPrior, planCaptor.getValue().getPriorState());
            assertEquals("ami-new", planned.get("ami"));
        }
    }

    // ---------------------------------------------------------------
    // 2. read()
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("read()")
    class Read {

        @Test
        @DisplayName("should call ReadResource with current state")
        void shouldCallReadResourceWithCurrentState() {
            // Given
            var input = camelCaseProps("ami-12345", "t2.micro");
            var responseState = encodeToDynamicValue(snakeCaseProps("ami-12345", "t2.micro"));
            var response = ReadResource.Response.newBuilder()
                    .setNewState(responseState)
                    .build();
            when(stub.readResource(any())).thenReturn(response);

            // When
            var result = handler.read(input);

            // Then
            var captor = ArgumentCaptor.forClass(ReadResource.Request.class);
            verify(stub).readResource(captor.capture());
            var request = captor.getValue();

            assertEquals(TF_TYPE_NAME, request.getTypeName());
            // Current state should be set (encoded from input)
            assertNotEquals(DynamicValue.getDefaultInstance(), request.getCurrentState());
            // Result should have camelCase keys
            assertEquals("ami-12345", result.get("ami"));
            assertEquals("t2.micro", result.get("instanceType"));
        }

        @Test
        @DisplayName("should return refreshed private data through the context")
        void shouldReturnRefreshedPrivateDataThroughContext() {
            // Given
            var input = camelCaseProps("ami-12345", "t2.micro");
            var newPrivateBytes = ByteString.copyFromUtf8("updated-private");
            var responseState = encodeToDynamicValue(snakeCaseProps("ami-12345", "t2.micro"));
            var response = ReadResource.Response.newBuilder()
                    .setNewState(responseState)
                    .setPrivate(newPrivateBytes)
                    .build();
            when(stub.readResource(any())).thenReturn(response);

            // When
            var firstContext = ResourceContext.<Map<String, Object>>empty();
            handler.read(input, firstContext);

            // Then — the refreshed bytes surface on the context for the engine
            // to persist; supplying them on a later read relays them to TF
            assertArrayEquals("updated-private".getBytes(StandardCharsets.UTF_8),
                    firstContext.privateDataToReturn());

            handler.read(input, ResourceContext.of(null, firstContext.privateDataToReturn()));

            var captor = ArgumentCaptor.forClass(ReadResource.Request.class);
            verify(stub, times(2)).readResource(captor.capture());
            var secondRequest = captor.getAllValues().get(1);
            assertEquals(newPrivateBytes, secondRequest.getPrivate());
        }
    }

    // ---------------------------------------------------------------
    // 3. update()
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("should apply with the engine-supplied prior state and never call ReadResource")
        void shouldUseEngineSuppliedPriorState() {
            // Given
            var input = camelCaseProps("ami-99999", "t3.large");
            var storedPrior = camelCaseProps("ami-12345", "t2.micro");

            var newState = encodeToDynamicValue(snakeCaseProps("ami-99999", "t3.large"));
            var applyResponse = ApplyResourceChange.Response.newBuilder()
                    .setNewState(newState)
                    .build();
            when(stub.applyResourceChange(any())).thenReturn(applyResponse);

            // When
            var result = handler.update(input, ResourceContext.of(storedPrior, null));

            // Then — the prior state comes straight from the engine
            verify(stub, never()).readResource(any());
            var applyCaptor = ArgumentCaptor.forClass(ApplyResourceChange.Request.class);
            verify(stub).applyResourceChange(applyCaptor.capture());
            var request = applyCaptor.getValue();

            assertEquals(TF_TYPE_NAME, request.getTypeName());
            assertEquals(encodeToDynamicValue(snakeCaseProps("ami-12345", "t2.micro")),
                    request.getPriorState());
            // Planned state should be the desired state
            assertNotEquals(DynamicValue.getDefaultInstance(), request.getPlannedState());
            // Config should be set
            assertNotEquals(DynamicValue.getDefaultInstance(), request.getConfig());
            // Result should contain updated values in camelCase
            assertEquals("ami-99999", result.get("ami"));
            assertEquals("t3.large", result.get("instanceType"));
        }
    }

    // ---------------------------------------------------------------
    // 4. delete()
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("should call ApplyResourceChange with prior state and null planned state")
        void shouldCallApplyWithNullPlannedState() {
            // Given
            var input = camelCaseProps("ami-12345", "t2.micro");
            var response = ApplyResourceChange.Response.newBuilder()
                    .setNewState(DynamicValue.getDefaultInstance())
                    .build();
            when(stub.applyResourceChange(any())).thenReturn(response);

            // When
            var result = handler.delete(input);

            // Then
            assertTrue(result);
            var captor = ArgumentCaptor.forClass(ApplyResourceChange.Request.class);
            verify(stub).applyResourceChange(captor.capture());
            var request = captor.getValue();

            assertEquals(TF_TYPE_NAME, request.getTypeName());
            // Prior state should be set (current resource state)
            assertNotEquals(DynamicValue.getDefaultInstance(), request.getPriorState());
            // Planned state should be msgpack nil (null cty value for destroy)
            assertTrue(request.getPlannedState().getMsgpack().size() > 0,
                    "Planned state should contain msgpack nil, not empty bytes");
            // Config should be msgpack nil for delete
            assertTrue(request.getConfig().getMsgpack().size() > 0,
                    "Config should contain msgpack nil, not empty bytes");
        }

        @Test
        @DisplayName("should relay engine-stored private data on delete")
        void shouldRelayPrivateDataOnDelete() {
            // Given — create returned private bytes that the engine persisted;
            // the delete happens later (possibly in a fresh process), so the
            // bytes arrive through the context, not an in-memory field
            var input = camelCaseProps("ami-12345", "t2.micro");
            var privateBytes = ByteString.copyFromUtf8("secret-state");
            var createState = encodeToDynamicValue(snakeCaseProps("ami-12345", "t2.micro"));
            var createResponse = ApplyResourceChange.Response.newBuilder()
                    .setNewState(createState)
                    .setPrivate(privateBytes)
                    .build();
            when(stub.applyResourceChange(any())).thenReturn(createResponse);
            var createContext = ResourceContext.<Map<String, Object>>empty();
            handler.create(input, createContext);

            // Now delete with the bytes the engine persisted after the create
            var deleteResponse = ApplyResourceChange.Response.newBuilder()
                    .setNewState(DynamicValue.getDefaultInstance())
                    .build();
            when(stub.applyResourceChange(any())).thenReturn(deleteResponse);

            handler.delete(input, ResourceContext.of(null, createContext.privateDataToReturn()));

            // Then — the destroy plan carries them; its planned private (echoed
            // by the default plan stub) is what the apply relays
            var captor = ArgumentCaptor.forClass(ApplyResourceChange.Request.class);
            verify(stub, times(2)).applyResourceChange(captor.capture());
            var deleteRequest = captor.getAllValues().get(1);
            assertEquals(privateBytes, deleteRequest.getPlannedPrivate());
        }
    }

    // ---------------------------------------------------------------
    // 5. Property conversion: camelCase in -> snake_case to TF -> camelCase out
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("Property name conversion")
    class PropertyConversion {

        @Test
        @DisplayName("should convert camelCase input to snake_case for TF and back")
        void shouldConvertPropertyNames() {
            // Given — input with camelCase
            var input = camelCaseProps("ami-abc", "t2.small");
            // Response from TF has snake_case keys encoded in msgpack
            var responseState = encodeToDynamicValue(snakeCaseProps("ami-abc", "t2.small"));
            var response = ApplyResourceChange.Response.newBuilder()
                    .setNewState(responseState)
                    .build();
            when(stub.applyResourceChange(any())).thenReturn(response);

            // When
            var result = handler.create(input);

            // Then — verify the request encodes snake_case
            var captor = ArgumentCaptor.forClass(ApplyResourceChange.Request.class);
            verify(stub).applyResourceChange(captor.capture());
            var request = captor.getValue();
            // Decode the planned state to verify snake_case keys
            var plannedBytes = request.getPlannedState().getMsgpack().toByteArray();
            var decodedPlanned = codec.decode(plannedBytes, SCHEMA_TYPE_JSON);
            assertTrue(decodedPlanned.containsKey("ami"));
            assertTrue(decodedPlanned.containsKey("instance_type"));
            assertFalse(decodedPlanned.containsKey("instanceType"));

            // Result should be back to camelCase
            assertTrue(result.containsKey("instanceType"));
            assertFalse(result.containsKey("instance_type"));
        }
    }

    // ---------------------------------------------------------------
    // 6. Diagnostics
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("Diagnostics handling")
    class Diagnostics {

        @Test
        @DisplayName("should throw RuntimeException when response has ERROR diagnostics")
        void shouldThrowOnErrorDiagnostics() {
            // Given
            var input = camelCaseProps("ami-bad", "t2.micro");
            var errorDiag = Diagnostic.newBuilder()
                    .setSeverity(Diagnostic.Severity.ERROR)
                    .setSummary("InvalidAMI")
                    .setDetail("AMI ami-bad does not exist")
                    .build();
            var response = ApplyResourceChange.Response.newBuilder()
                    .addDiagnostics(errorDiag)
                    .build();
            when(stub.applyResourceChange(any())).thenReturn(response);

            // When/Then
            var exception = assertThrows(RuntimeException.class, () -> handler.create(input));
            assertTrue(exception.getMessage().contains("InvalidAMI"));
            assertTrue(exception.getMessage().contains("ami-bad does not exist"));
            assertTrue(exception.getMessage().contains("create"));
        }

        @Test
        @DisplayName("should concatenate multiple ERROR diagnostics in exception message")
        void shouldConcatenateMultipleErrors() {
            // Given
            var input = camelCaseProps("", "");
            var error1 = Diagnostic.newBuilder()
                    .setSeverity(Diagnostic.Severity.ERROR)
                    .setSummary("MissingAMI")
                    .setDetail("ami is required")
                    .build();
            var error2 = Diagnostic.newBuilder()
                    .setSeverity(Diagnostic.Severity.ERROR)
                    .setSummary("MissingType")
                    .setDetail("instance_type is required")
                    .build();
            var response = ApplyResourceChange.Response.newBuilder()
                    .addDiagnostics(error1)
                    .addDiagnostics(error2)
                    .build();
            when(stub.applyResourceChange(any())).thenReturn(response);

            // When/Then
            var exception = assertThrows(RuntimeException.class, () -> handler.create(input));
            assertTrue(exception.getMessage().contains("MissingAMI"));
            assertTrue(exception.getMessage().contains("MissingType"));
        }

        @Test
        @DisplayName("should not throw on WARNING-only diagnostics")
        void shouldNotThrowOnWarnings() {
            // Given
            var input = camelCaseProps("ami-12345", "t2.micro");
            var warning = Diagnostic.newBuilder()
                    .setSeverity(Diagnostic.Severity.WARNING)
                    .setSummary("Deprecated")
                    .setDetail("t2.micro is deprecated")
                    .build();
            var responseState = encodeToDynamicValue(snakeCaseProps("ami-12345", "t2.micro"));
            var response = ApplyResourceChange.Response.newBuilder()
                    .setNewState(responseState)
                    .addDiagnostics(warning)
                    .build();
            when(stub.applyResourceChange(any())).thenReturn(response);

            // When — should not throw
            var result = handler.create(input);

            // Then
            assertNotNull(result);
            assertEquals("ami-12345", result.get("ami"));
        }
    }

    // ---------------------------------------------------------------
    // 7. Private data lifecycle
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("Private data lifecycle")
    class PrivateData {

        @Test
        @DisplayName("should have empty private data before any operation")
        void shouldHaveEmptyPrivateDataInitially() {
            // Given — handler with no prior operations
            var input = camelCaseProps("ami-12345", "t2.micro");
            var responseState = encodeToDynamicValue(snakeCaseProps("ami-12345", "t2.micro"));
            var response = ReadResource.Response.newBuilder()
                    .setNewState(responseState)
                    .build();
            when(stub.readResource(any())).thenReturn(response);

            // When
            handler.read(input);

            // Then — private data should be empty ByteString
            var captor = ArgumentCaptor.forClass(ReadResource.Request.class);
            verify(stub).readResource(captor.capture());
            assertEquals(ByteString.EMPTY, captor.getValue().getPrivate());
        }
    }

    // ---------------------------------------------------------------
    // 8. getTypeName()
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("getTypeName()")
    class TypeName {

        @Test
        @DisplayName("should return the Kite type name")
        void shouldReturnKiteTypeName() {
            assertEquals(KITE_TYPE_NAME, handler.getTypeName());
        }
    }

    // ---------------------------------------------------------------
    // 9. TerraformDataSourceHandler
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("TerraformDataSourceHandler")
    class DataSource {

        private static final String DS_TF_TYPE = "aws_ami";
        private static final String DS_KITE_TYPE = "Ami";
        private static final String DS_SCHEMA = """
                ["object", {"name": "string", "owner_id": "string"}]""";

        private TerraformDataSourceHandler dataSourceHandler;

        @BeforeEach
        void setUp() {
            dataSourceHandler = new TerraformDataSourceHandler(DS_TF_TYPE, DS_KITE_TYPE, client, DS_SCHEMA);
        }

        @Test
        @DisplayName("read() should call ReadDataSource with encoded config")
        void shouldCallReadDataSource() {
            // Given
            var input = new LinkedHashMap<String, Object>();
            input.put("name", "my-ami");
            input.put("ownerId", "123456");

            var responseSnake = new LinkedHashMap<String, Object>();
            responseSnake.put("name", "my-ami");
            responseSnake.put("owner_id", "123456");
            var responseBytes = codec.encode(responseSnake, DS_SCHEMA);
            var responseDv = DynamicValue.newBuilder()
                    .setMsgpack(ByteString.copyFrom(responseBytes))
                    .build();
            var response = ReadDataSource.Response.newBuilder()
                    .setState(responseDv)
                    .build();
            when(stub.readDataSource(any())).thenReturn(response);

            // When
            var result = dataSourceHandler.read(input);

            // Then
            var captor = ArgumentCaptor.forClass(ReadDataSource.Request.class);
            verify(stub).readDataSource(captor.capture());
            var request = captor.getValue();
            assertEquals(DS_TF_TYPE, request.getTypeName());
            assertNotEquals(DynamicValue.getDefaultInstance(), request.getConfig());

            assertEquals("my-ami", result.get("name"));
            assertEquals("123456", result.get("ownerId"));
        }

        @Test
        @DisplayName("create() should throw UnsupportedOperationException")
        void createShouldThrow() {
            var input = Map.<String, Object>of("name", "test");
            assertThrows(UnsupportedOperationException.class, () -> dataSourceHandler.create(input));
        }

        @Test
        @DisplayName("update() should throw UnsupportedOperationException")
        void updateShouldThrow() {
            var input = Map.<String, Object>of("name", "test");
            assertThrows(UnsupportedOperationException.class, () -> dataSourceHandler.update(input));
        }

        @Test
        @DisplayName("delete() should throw UnsupportedOperationException")
        void deleteShouldThrow() {
            var input = Map.<String, Object>of("name", "test");
            assertThrows(UnsupportedOperationException.class, () -> dataSourceHandler.delete(input));
        }

        @Test
        @DisplayName("should throw on ERROR diagnostics from ReadDataSource")
        void shouldThrowOnErrorDiagnostics() {
            // Given
            var input = Map.<String, Object>of("name", "bad-ami");
            var errorDiag = Diagnostic.newBuilder()
                    .setSeverity(Diagnostic.Severity.ERROR)
                    .setSummary("NotFound")
                    .setDetail("Data source not found")
                    .build();
            var response = ReadDataSource.Response.newBuilder()
                    .addDiagnostics(errorDiag)
                    .build();
            when(stub.readDataSource(any())).thenReturn(response);

            // When/Then
            var exception = assertThrows(RuntimeException.class, () -> dataSourceHandler.read(input));
            assertTrue(exception.getMessage().contains("NotFound"));
            assertTrue(exception.getMessage().contains("read"));
        }

        @Test
        @DisplayName("should return Kite type name")
        void shouldReturnKiteTypeName() {
            assertEquals(DS_KITE_TYPE, dataSourceHandler.getTypeName());
        }
    }
}
