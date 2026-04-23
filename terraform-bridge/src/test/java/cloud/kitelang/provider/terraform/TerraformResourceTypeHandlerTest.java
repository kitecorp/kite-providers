package cloud.kitelang.provider.terraform;

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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        lenient().when(client.getStub()).thenReturn(stub);
        codec = new CtyCodec();
        handler = new TerraformResourceTypeHandler(TF_TYPE_NAME, KITE_TYPE_NAME, client, SCHEMA_TYPE_JSON);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

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
        @DisplayName("should store private data from create response")
        void shouldStorePrivateDataFromResponse() {
            // Given
            var input = camelCaseProps("ami-12345", "t2.micro");
            var privateBytes = ByteString.copyFromUtf8("opaque-provider-state");
            var responseState = encodeToDynamicValue(snakeCaseProps("ami-12345", "t2.micro"));
            var response = ApplyResourceChange.Response.newBuilder()
                    .setNewState(responseState)
                    .setPrivate(privateBytes)
                    .build();
            when(stub.applyResourceChange(any())).thenReturn(response);

            // When
            handler.create(input);

            // Then — verify private data is relayed on subsequent read
            var readResponseState = encodeToDynamicValue(snakeCaseProps("ami-12345", "t2.micro"));
            var readResponse = ReadResource.Response.newBuilder()
                    .setNewState(readResponseState)
                    .setPrivate(privateBytes)
                    .build();
            when(stub.readResource(any())).thenReturn(readResponse);

            handler.read(input);

            var readCaptor = ArgumentCaptor.forClass(ReadResource.Request.class);
            verify(stub).readResource(readCaptor.capture());
            assertEquals(privateBytes, readCaptor.getValue().getPrivate());
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
        @DisplayName("should update private data from read response")
        void shouldUpdatePrivateDataFromReadResponse() {
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
            handler.read(input);

            // Then — read again and verify the updated private bytes are sent
            var secondResponse = ReadResource.Response.newBuilder()
                    .setNewState(responseState)
                    .build();
            when(stub.readResource(any())).thenReturn(secondResponse);

            handler.read(input);

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
        @DisplayName("should call ReadResource for prior state then ApplyResourceChange with both states")
        void shouldReadPriorStateThenApply() {
            // Given
            var input = camelCaseProps("ami-99999", "t3.large");

            // Mock the read call (to fetch prior state)
            var priorState = encodeToDynamicValue(snakeCaseProps("ami-12345", "t2.micro"));
            var readResponse = ReadResource.Response.newBuilder()
                    .setNewState(priorState)
                    .build();
            when(stub.readResource(any())).thenReturn(readResponse);

            // Mock the apply call
            var newState = encodeToDynamicValue(snakeCaseProps("ami-99999", "t3.large"));
            var applyResponse = ApplyResourceChange.Response.newBuilder()
                    .setNewState(newState)
                    .build();
            when(stub.applyResourceChange(any())).thenReturn(applyResponse);

            // When
            var result = handler.update(input);

            // Then
            verify(stub).readResource(any());
            var applyCaptor = ArgumentCaptor.forClass(ApplyResourceChange.Request.class);
            verify(stub).applyResourceChange(applyCaptor.capture());
            var request = applyCaptor.getValue();

            assertEquals(TF_TYPE_NAME, request.getTypeName());
            // Prior state should be the read result
            assertNotEquals(DynamicValue.getDefaultInstance(), request.getPriorState());
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
        @DisplayName("should relay private data on delete")
        void shouldRelayPrivateDataOnDelete() {
            // Given — first create with private data
            var input = camelCaseProps("ami-12345", "t2.micro");
            var privateBytes = ByteString.copyFromUtf8("secret-state");
            var createState = encodeToDynamicValue(snakeCaseProps("ami-12345", "t2.micro"));
            var createResponse = ApplyResourceChange.Response.newBuilder()
                    .setNewState(createState)
                    .setPrivate(privateBytes)
                    .build();
            when(stub.applyResourceChange(any())).thenReturn(createResponse);
            handler.create(input);

            // Now delete
            var deleteResponse = ApplyResourceChange.Response.newBuilder()
                    .setNewState(DynamicValue.getDefaultInstance())
                    .build();
            when(stub.applyResourceChange(any())).thenReturn(deleteResponse);

            handler.delete(input);

            // Then
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
