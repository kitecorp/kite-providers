package cloud.kitelang.provider.terraform.integration;

import cloud.kitelang.provider.terraform.CtyCodec;
import cloud.kitelang.provider.terraform.GoPluginClient;
import cloud.kitelang.provider.terraform.TerraformBridgeProvider;
import cloud.kitelang.provider.terraform.TerraformPropertyMapper;
import cloud.kitelang.provider.terraform.TerraformRegistryClient;
import cloud.kitelang.provider.terraform.TerraformResourceTypeHandler;
import cloud.kitelang.provider.terraform.TerraformSchemaConverter;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.*;
import org.msgpack.core.MessagePack;
import tfplugin5.Tfplugin5.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests exercising the full Terraform Bridge pipeline against a real
 * {@code hashicorp/random} provider binary.
 *
 * <p>The {@code random} provider is ideal for integration testing because:
 * <ul>
 *   <li>No cloud credentials required (generates values locally)</li>
 *   <li>Simple schema ({@code random_string}, {@code random_integer}, {@code random_pet})</li>
 *   <li>Fast execution (no network calls to cloud APIs)</li>
 *   <li>Available on all platforms</li>
 *   <li>Exercises the full CRUD lifecycle</li>
 * </ul>
 *
 * <p>If the provider binary cannot be downloaded (e.g. network issues in CI),
 * all tests in this class are skipped gracefully via {@code Assumptions}.</p>
 *
 * @see GoPluginClient
 * @see TerraformRegistryClient
 * @see TerraformResourceTypeHandler
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TerraformBridgeIntegrationTest {

    private static final String PROVIDER_ADDRESS = "hashicorp/random";

    /** Path to the downloaded random provider binary, or null if download failed. */
    private Path providerBinaryPath;

    /** Reused GoPluginClient across tests that need it. */
    private GoPluginClient client;

    // ------------------------------------------------------------------
    // Setup: download provider once before all tests
    // ------------------------------------------------------------------

    @BeforeAll
    void downloadProvider() {
        try {
            var cacheDir = Files.createTempDirectory("kite-integration-test-providers");
            var registryClient = new TerraformRegistryClient(cacheDir);
            providerBinaryPath = registryClient.ensureProvider(PROVIDER_ADDRESS, null);
        } catch (Exception e) {
            // Network failure or platform mismatch -- tests will be skipped
            System.err.println("Failed to download random provider (tests will be skipped): " + e.getMessage());
            providerBinaryPath = null;
        }
    }

    @AfterEach
    void closeClient() {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    /**
     * Helper: assumes the provider binary was downloaded successfully.
     * Skips the calling test if it was not.
     */
    private void assumeProviderAvailable() {
        Assumptions.assumeTrue(
                providerBinaryPath != null && Files.isExecutable(providerBinaryPath),
                "Random provider binary not available (download may have failed)"
        );
    }

    /**
     * Helper: launches a new GoPluginClient pointing at the downloaded binary.
     * Sets the {@link #client} field so {@link #closeClient()} can clean up.
     */
    private GoPluginClient launchClient() {
        client = new GoPluginClient(providerBinaryPath);
        return client;
    }

    /**
     * Builds a {@link DynamicValue} containing msgpack nil (null cty value).
     * Used as the prior state for create operations and planned state for delete.
     */
    private DynamicValue nullDynamicValue() {
        try (var packer = MessagePack.newDefaultBufferPacker()) {
            packer.packNil();
            return DynamicValue.newBuilder()
                    .setMsgpack(ByteString.copyFrom(packer.toByteArray()))
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("Failed to pack nil value", e);
        }
    }

    /**
     * Builds the cty object type JSON string from a Terraform schema block.
     * Replicates the logic in {@link TerraformBridgeProvider} so integration tests
     * can encode/decode DynamicValues independently.
     *
     * @param block the Terraform schema block
     * @return JSON cty object type string, e.g. {@code ["object",{"length":"number","result":"string"}]}
     */
    private String buildObjectType(Schema.Block block) {
        var sb = new StringBuilder("[\"object\",{");
        var first = true;
        for (var attr : block.getAttributesList()) {
            if (!first) {
                sb.append(',');
            }
            sb.append('"').append(attr.getName()).append("\":");
            sb.append(new String(attr.getType().toByteArray(), StandardCharsets.UTF_8));
            first = false;
        }
        sb.append("}]");
        return sb.toString();
    }

    // ==================================================================
    // Test 1: GoPluginClient handshake with real provider
    // ==================================================================

    @Test
    @Order(1)
    @DisplayName("should launch and handshake with the random provider binary")
    void launchAndHandshakeWithRandomProvider() {
        assumeProviderAvailable();

        var pluginClient = launchClient();

        assertTrue(pluginClient.isHealthy(), "Provider should be healthy after handshake");
        var appProtocol = pluginClient.getAppProtocolVersion();
        assertTrue(
                appProtocol == 5 || appProtocol == 6,
                "App protocol version should be 5 or 6, got: " + appProtocol
        );
    }

    // ==================================================================
    // Test 2: Fetch schema from real provider
    // ==================================================================

    @Test
    @Order(2)
    @DisplayName("should fetch schema from random provider with expected resource types")
    void fetchSchemaFromRandomProvider() {
        assumeProviderAvailable();
        var pluginClient = launchClient();
        var stub = pluginClient.getStub();

        var response = stub.getSchema(GetProviderSchema.Request.getDefaultInstance());

        // Verify key resource schemas exist
        var resourceSchemas = response.getResourceSchemasMap();
        assertTrue(resourceSchemas.containsKey("random_string"),
                "Schema should contain random_string resource");
        assertTrue(resourceSchemas.containsKey("random_integer"),
                "Schema should contain random_integer resource");
        assertTrue(resourceSchemas.containsKey("random_pet"),
                "Schema should contain random_pet resource");

        // Verify random_string has expected attributes
        var stringSchema = resourceSchemas.get("random_string");
        var attrNames = stringSchema.getBlock().getAttributesList().stream()
                .map(Schema.Attribute::getName)
                .toList();
        assertTrue(attrNames.contains("length"),
                "random_string should have 'length' attribute, found: " + attrNames);
        assertTrue(attrNames.contains("result"),
                "random_string should have 'result' attribute, found: " + attrNames);
    }

    // ==================================================================
    // Test 3: Schema conversion produces valid Kite type names and schemas
    // ==================================================================

    @Test
    @Order(3)
    @DisplayName("should convert random provider schemas to valid Kite type names and DSL")
    void convertRandomProviderSchemas() {
        assumeProviderAvailable();
        var pluginClient = launchClient();
        var stub = pluginClient.getStub();

        var response = stub.getSchema(GetProviderSchema.Request.getDefaultInstance());
        var converter = new TerraformSchemaConverter("random");

        // Verify type name conversions (prefix stripped, PascalCase)
        assertEquals("String", converter.toKiteTypeName("random_string"));
        assertEquals("Integer", converter.toKiteTypeName("random_integer"));
        assertEquals("Pet", converter.toKiteTypeName("random_pet"));

        // Convert random_string schema to Kite DSL
        var stringSchema = response.getResourceSchemasMap().get("random_string");
        var kiteSchemaDsl = converter.toKiteSchema("random_string", stringSchema);

        // The DSL should contain type declarations and decorators
        assertTrue(kiteSchemaDsl.contains("string"),
                "Kite schema should reference 'string' type in property declarations");
        assertTrue(kiteSchemaDsl.contains("@cloud"),
                "Kite schema should have @cloud decorator for computed properties like 'result'");
        assertTrue(kiteSchemaDsl.contains("schema String"),
                "Kite schema should start with 'schema String'");
    }

    // ==================================================================
    // Test 4: Full CRUD lifecycle with random_string via raw gRPC
    // ==================================================================

    @Test
    @Order(4)
    @DisplayName("should create, read, and delete a random_string via raw gRPC calls")
    void createReadDeleteRandomString() {
        assumeProviderAvailable();
        var pluginClient = launchClient();
        var stub = pluginClient.getStub();
        var codec = new CtyCodec();

        // 1. Configure the provider (random needs no real config, but the call is required)
        var schemaResponse = stub.getSchema(GetProviderSchema.Request.getDefaultInstance());
        var providerConfigType = buildObjectType(schemaResponse.getProvider().getBlock());
        var emptyConfig = codec.encode(Map.of(), providerConfigType);
        var configRequest = Configure.Request.newBuilder()
                .setTerraformVersion("1.9.0")
                .setConfig(DynamicValue.newBuilder()
                        .setMsgpack(ByteString.copyFrom(emptyConfig))
                        .build())
                .build();
        var configResponse = stub.configure(configRequest);
        assertNoErrors(configResponse.getDiagnosticsList(), "configure");

        // 2. Build cty object type from the random_string schema
        var stringSchema = schemaResponse.getResourceSchemasMap().get("random_string");
        var schemaTypeJson = buildObjectType(stringSchema.getBlock());

        // 3. Build proposed resource properties
        //    We populate only the user-configurable attributes; computed attributes are null/unknown.
        var allAttrNames = stringSchema.getBlock().getAttributesList().stream()
                .map(Schema.Attribute::getName)
                .toList();
        var proposedProps = new LinkedHashMap<String, Object>();
        for (var attrName : allAttrNames) {
            proposedProps.put(attrName, null);
        }
        // Set the fields we want to configure
        proposedProps.put("length", 16);
        proposedProps.put("upper", true);
        proposedProps.put("lower", true);
        proposedProps.put("numeric", true);
        proposedProps.put("special", false);

        var proposedBytes = codec.encode(proposedProps, schemaTypeJson);
        var proposedDv = DynamicValue.newBuilder()
                .setMsgpack(ByteString.copyFrom(proposedBytes))
                .build();

        // 4. PlanResourceChange (required before Apply)
        //    Prior state must be msgpack nil (not empty bytes) for create operations
        var nilState = nullDynamicValue();
        var planRequest = PlanResourceChange.Request.newBuilder()
                .setTypeName("random_string")
                .setPriorState(nilState)
                .setProposedNewState(proposedDv)
                .setConfig(proposedDv)
                .build();
        var planResponse = stub.planResourceChange(planRequest);
        assertNoErrors(planResponse.getDiagnosticsList(), "plan");

        var plannedState = planResponse.getPlannedState();
        assertNotEquals(DynamicValue.getDefaultInstance(), plannedState,
                "Planned state should not be empty");

        // 5. ApplyResourceChange to create the resource
        var applyRequest = ApplyResourceChange.Request.newBuilder()
                .setTypeName("random_string")
                .setPriorState(nilState)
                .setPlannedState(plannedState)
                .setConfig(proposedDv)
                .setPlannedPrivate(planResponse.getPlannedPrivate())
                .build();
        var applyResponse = stub.applyResourceChange(applyRequest);
        assertNoErrors(applyResponse.getDiagnosticsList(), "apply (create)");

        // 6. Decode and verify the created state
        var newStateBytes = applyResponse.getNewState().getMsgpack().toByteArray();
        var createdState = codec.decode(newStateBytes, schemaTypeJson);
        assertNotNull(createdState.get("result"),
                "Created random_string should have a 'result' value");
        var resultValue = createdState.get("result").toString();
        assertEquals(16, resultValue.length(),
                "Result should be 16 characters long, got: " + resultValue);
        assertNotNull(createdState.get("id"),
                "Created random_string should have an 'id' value");

        // 7. ReadResource to verify state
        var readRequest = ReadResource.Request.newBuilder()
                .setTypeName("random_string")
                .setCurrentState(applyResponse.getNewState())
                .setPrivate(applyResponse.getPrivate())
                .build();
        var readResponse = stub.readResource(readRequest);
        assertNoErrors(readResponse.getDiagnosticsList(), "read");

        var readStateBytes = readResponse.getNewState().getMsgpack().toByteArray();
        var readState = codec.decode(readStateBytes, schemaTypeJson);
        assertEquals(resultValue, readState.get("result").toString(),
                "Read state result should match created state result");

        // 8. Plan + Apply to delete (planned_state = msgpack nil)
        var deletePlanRequest = PlanResourceChange.Request.newBuilder()
                .setTypeName("random_string")
                .setPriorState(applyResponse.getNewState())
                .setProposedNewState(nilState)
                .setConfig(nilState)
                .setPriorPrivate(applyResponse.getPrivate())
                .build();
        var deletePlanResponse = stub.planResourceChange(deletePlanRequest);
        assertNoErrors(deletePlanResponse.getDiagnosticsList(), "plan (delete)");

        var deleteRequest = ApplyResourceChange.Request.newBuilder()
                .setTypeName("random_string")
                .setPriorState(applyResponse.getNewState())
                .setPlannedState(deletePlanResponse.getPlannedState())
                .setConfig(nilState)
                .setPlannedPrivate(deletePlanResponse.getPlannedPrivate())
                .build();
        var deleteResponse = stub.applyResourceChange(deleteRequest);
        assertNoErrors(deleteResponse.getDiagnosticsList(), "apply (delete)");
    }

    // ==================================================================
    // Test 5: Full lifecycle via TerraformResourceTypeHandler (bridge API)
    // ==================================================================

    @Test
    @Order(5)
    @DisplayName("should create, read, and delete random_string via TerraformResourceTypeHandler")
    void fullLifecycleViaBridgeHandler() {
        assumeProviderAvailable();
        var pluginClient = launchClient();
        var stub = pluginClient.getStub();
        var codec = new CtyCodec();

        // 1. Configure the provider
        var schemaResponse = stub.getSchema(GetProviderSchema.Request.getDefaultInstance());
        var providerConfigType = buildObjectType(schemaResponse.getProvider().getBlock());
        var emptyConfig = codec.encode(Map.of(), providerConfigType);
        var configRequest = Configure.Request.newBuilder()
                .setTerraformVersion("1.9.0")
                .setConfig(DynamicValue.newBuilder()
                        .setMsgpack(ByteString.copyFrom(emptyConfig))
                        .build())
                .build();
        stub.configure(configRequest);

        // 2. Build the handler for random_string
        var stringSchema = schemaResponse.getResourceSchemasMap().get("random_string");
        var schemaTypeJson = buildObjectType(stringSchema.getBlock());
        var handler = new TerraformResourceTypeHandler(
                "random_string", "String", pluginClient, schemaTypeJson
        );

        // 3. Create: build camelCase property map
        var allAttrNames = stringSchema.getBlock().getAttributesList().stream()
                .map(Schema.Attribute::getName)
                .toList();
        var createProps = new LinkedHashMap<String, Object>();
        for (var attrName : allAttrNames) {
            createProps.put(TerraformPropertyMapper.toCamelCase(attrName), null);
        }
        createProps.put("length", 12);
        createProps.put("upper", true);
        createProps.put("lower", true);
        createProps.put("numeric", false);
        createProps.put("special", false);

        var created = handler.create(createProps);
        assertNotNull(created.get("result"),
                "Created result should be populated");
        var resultStr = created.get("result").toString();
        assertEquals(12, resultStr.length(),
                "Result string should be 12 characters, got: " + resultStr);
        // Verify only alphabetic characters (no digits, no special)
        assertTrue(resultStr.matches("[a-zA-Z]+"),
                "Result should only contain letters when numeric=false, special=false, got: " + resultStr);

        // 4. Read: pass the full created state back
        var readResult = handler.read(created);
        assertEquals(resultStr, readResult.get("result").toString(),
                "Read result should match created result");

        // 5. Delete: pass the state for deletion
        var deleted = handler.delete(created);
        assertTrue(deleted, "Delete should return true");
    }

    // ==================================================================
    // Test 6: TerraformBridgeProvider full init
    // ==================================================================

    @Test
    @Order(6)
    @DisplayName("should initialise TerraformBridgeProvider and register random provider types")
    void bridgeProviderInitWithRealProvider() throws Exception {
        assumeProviderAvailable();

        // The TerraformBridgeProvider(String, GoPluginClient) constructor is package-private.
        // Use reflection to invoke it from this sub-package.
        var pluginClient = new GoPluginClient(providerBinaryPath);
        var constructor = TerraformBridgeProvider.class.getDeclaredConstructor(
                String.class, GoPluginClient.class);
        constructor.setAccessible(true);
        var provider = constructor.newInstance("random", pluginClient);

        try {
            provider.init();

            // Verify that expected resource types were registered
            assertNotNull(provider.getResourceType("String"),
                    "Provider should have registered 'String' (from random_string)");
            assertNotNull(provider.getResourceType("Integer"),
                    "Provider should have registered 'Integer' (from random_integer)");
            assertNotNull(provider.getResourceType("Pet"),
                    "Provider should have registered 'Pet' (from random_pet)");

            // Verify schema DSL strings were generated
            var schemaStrings = provider.getSchemaStrings();
            assertFalse(schemaStrings.isEmpty(),
                    "Schema strings map should not be empty after init");
            assertTrue(schemaStrings.containsKey("String"),
                    "Schema strings should contain 'String' type");
            var stringDsl = schemaStrings.get("String");
            assertTrue(stringDsl.contains("schema String"),
                    "String schema DSL should start with 'schema String'");
        } finally {
            provider.stop();
        }
    }

    // ------------------------------------------------------------------
    // Assertion helper: verify no ERROR diagnostics
    // ------------------------------------------------------------------

    /**
     * Asserts that the diagnostics list contains no ERROR-severity entries.
     * Warnings are logged but do not fail the test.
     *
     * @param diagnostics the diagnostics from a Terraform RPC response
     * @param operation   the operation name for error messages
     */
    private void assertNoErrors(java.util.List<Diagnostic> diagnostics, String operation) {
        var errors = diagnostics.stream()
                .filter(d -> d.getSeverity() == Diagnostic.Severity.ERROR)
                .toList();
        if (!errors.isEmpty()) {
            var message = errors.stream()
                    .map(d -> d.getSummary() + ": " + d.getDetail())
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("");
            fail("Terraform " + operation + " returned errors: " + message);
        }
    }
}
