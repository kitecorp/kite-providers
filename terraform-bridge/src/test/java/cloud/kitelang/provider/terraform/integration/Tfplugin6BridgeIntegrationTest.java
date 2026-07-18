package cloud.kitelang.provider.terraform.integration;

import cloud.kitelang.provider.ResourceContext;
import cloud.kitelang.provider.terraform.GoPluginClient;
import cloud.kitelang.provider.terraform.TerraformBridgeProvider;
import cloud.kitelang.provider.terraform.TerraformDataSourceHandler;
import cloud.kitelang.provider.terraform.TerraformPropertyMapper;
import cloud.kitelang.provider.terraform.TerraformRegistryClient;
import cloud.kitelang.provider.terraform.TerraformResourceTypeHandler;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests exercising the Terraform Bridge against a <em>protocol-6-only</em>
 * provider: {@code hashicorp/tfcoremock}. The registry download metadata for the
 * pinned version reports {@code "protocols": ["6.0"]}, so every RPC in these tests
 * necessarily travels over tfplugin6 — proving the version-agnostic facade selects
 * and speaks protocol 6 from the handshake alone.
 *
 * <p>{@code tfcoremock} is HashiCorp's mock provider for testing Terraform core:
 * fully local (resources persist as JSON files in a configurable directory), no
 * cloud credentials, and {@code tfcoremock_simple_resource} supports a complete
 * CRUD cycle. Its {@code tfcoremock_complex_resource} uses tfplugin6 nested
 * attributes, so a successful {@code init()} also covers the nested-attribute
 * schema synthesis against a real provider.</p>
 *
 * <p>If the provider binary cannot be downloaded (e.g. network issues in CI),
 * all tests in this class are skipped gracefully via {@code Assumptions}.</p>
 *
 * @see cloud.kitelang.provider.terraform.Tfplugin6Rpc
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Tfplugin6BridgeIntegrationTest {

    private static final String PROVIDER_ADDRESS = "hashicorp/tfcoremock";

    /** Pinned so the registry's protocols=["6.0"] evidence stays tied to what runs. */
    private static final String PROVIDER_VERSION = "0.5.0";

    /** Path to the downloaded tfcoremock provider binary, or null if download failed. */
    private Path providerBinaryPath;

    /** Where tfcoremock persists its resource JSON files (kept out of the repo tree). */
    private Path resourceDirectory;

    /** Client owned by the currently running test, closed in {@link #closeClient()}. */
    private GoPluginClient client;

    @BeforeAll
    void downloadProvider() {
        try {
            var cacheDir = Files.createTempDirectory("kite-tfplugin6-test-providers");
            resourceDirectory = Files.createTempDirectory("kite-tfplugin6-test-resources");
            var registryClient = new TerraformRegistryClient(cacheDir);
            providerBinaryPath = registryClient.ensureProvider(PROVIDER_ADDRESS, PROVIDER_VERSION);
        } catch (Exception e) {
            // Network failure or platform mismatch -- tests will be skipped
            System.err.println("Failed to download tfcoremock provider (tests will be skipped): " + e.getMessage());
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

    private void assumeProviderAvailable() {
        Assumptions.assumeTrue(
                providerBinaryPath != null && Files.isExecutable(providerBinaryPath),
                "tfcoremock provider binary not available (download may have failed)"
        );
    }

    // ==================================================================
    // Test 1: handshake negotiates protocol 6
    // ==================================================================

    @Test
    @Order(1)
    @DisplayName("should negotiate app protocol 6 with the protocol-6-only tfcoremock provider")
    void handshakeNegotiatesProtocol6() {
        assumeProviderAvailable();

        client = new GoPluginClient(providerBinaryPath);

        assertTrue(client.isHealthy(), "Provider should be healthy after handshake");
        assertEquals(6, client.getAppProtocolVersion(),
                "tfcoremock only serves protocol 6, so the handshake must negotiate 6");
    }

    // ==================================================================
    // Test 2: schema fetch + registration through the full bridge
    // ==================================================================

    @Test
    @Order(2)
    @DisplayName("should initialise the bridge over tfplugin6 and register tfcoremock resource types")
    void bridgeInitRegistersTfcoremockTypes() throws Exception {
        assumeProviderAvailable();
        var provider = newBridgeProvider();

        try {
            provider.init();

            assertNotNull(provider.getResourceType("SimpleResource"),
                    "tfcoremock_simple_resource should register as SimpleResource");
            // complex_resource uses tfplugin6 nested attributes; registering it
            // proves the nested_type -> cty type synthesis holds up against a
            // real provider schema
            assertNotNull(provider.getResourceType("ComplexResource"),
                    "tfcoremock_complex_resource should register as ComplexResource");

            var schemaStrings = provider.getSchemaStrings();
            assertTrue(schemaStrings.containsKey("SimpleResource"),
                    "Schema strings should contain 'SimpleResource'");
            assertTrue(schemaStrings.get("SimpleResource").startsWith("schema SimpleResource {"),
                    "SimpleResource DSL should start with the schema declaration, got: "
                            + schemaStrings.get("SimpleResource"));
        } finally {
            provider.stop();
            client = null; // stop() closed the client; prevent a double close
        }
    }

    // ==================================================================
    // Test 3: full CRUD cycle over tfplugin6 via the bridge handler
    // ==================================================================

    @Test
    @Order(3)
    @DisplayName("should create, read, update, and delete a simple resource over tfplugin6")
    void fullCrudCycleOverTfplugin6() throws Exception {
        assumeProviderAvailable();
        var provider = newBridgeProvider();

        try {
            provider.init();
            provider.configure(providerConfig(provider));

            // Registry lookup — a same-named data source no longer overwrites
            // the resource handler (kitecorp/kite-providers#13)
            var handler = (TerraformResourceTypeHandler) provider.<Map<String, Object>>getResourceType("SimpleResource");
            var attributeNames = simpleResourceAttributeNames();
            assertTrue(attributeNames.containsAll(java.util.List.of("string", "integer", "bool", "id")),
                    "tfcoremock_simple_resource should have string/integer/bool/id attributes, found: "
                            + attributeNames);

            // --- Create
            var createProps = propsWithNulls(attributeNames);
            createProps.put("string", "kite-tfplugin6");
            createProps.put("integer", 42);
            createProps.put("bool", true);

            var createContext = ResourceContext.<Map<String, Object>>empty();
            var created = handler.create(createProps, createContext);

            assertEquals("kite-tfplugin6", created.get("string"));
            assertEquals(42, ((Number) created.get("integer")).intValue());
            assertEquals(true, created.get("bool"));
            var createdId = created.get("id");
            assertNotNull(createdId, "tfcoremock should generate an id for the created resource");

            // --- Read
            var readContext = ResourceContext.<Map<String, Object>>of(null, createContext.privateDataToReturn());
            var read = handler.read(created, readContext);
            assertEquals("kite-tfplugin6", read.get("string"));
            assertEquals(createdId, read.get("id"), "Read should return the created resource's id");

            // --- Update (id pinned in the desired props keeps the update deterministic)
            var desired = propsWithNulls(attributeNames);
            desired.put("string", "kite-tfplugin6-updated");
            desired.put("integer", 42);
            desired.put("bool", true);
            desired.put("id", createdId);

            var updateContext = ResourceContext.of(created, createContext.privateDataToReturn());
            var updated = handler.update(desired, updateContext);
            assertEquals("kite-tfplugin6-updated", updated.get("string"));
            assertEquals(createdId, updated.get("id"), "Update should keep the resource's id");

            // --- Delete
            var deleteContext = ResourceContext.<Map<String, Object>>of(null,
                    updateContext.privateDataToReturn());
            assertTrue(handler.delete(updated, deleteContext), "Delete should return true");
        } finally {
            provider.stop();
            client = null; // stop() closed the client; prevent a double close
        }
    }

    // ==================================================================
    // Test 4: provider config schema exposes tfcoremock's real attributes
    // ==================================================================

    @Test
    @Order(4)
    @DisplayName("should expose tfcoremock's provider config schema attributes")
    void providerConfigSchemaExposesRealAttributes() throws Exception {
        assumeProviderAvailable();
        newBridgeProvider().init();

        try {
            var providerSchema = client.rpc().getProviderSchema().provider();
            assertNotNull(providerSchema, "tfcoremock should expose a provider config schema");

            var attributeNames = providerSchema.block().attributes().stream()
                    .map(cloud.kitelang.provider.terraform.TfAttribute::name)
                    .sorted()
                    .toList();
            // Pinned to tfcoremock 0.5.0's published provider block
            assertEquals(java.util.List.of(
                            "data_directory", "fail_on_create", "fail_on_delete",
                            "fail_on_read", "fail_on_update",
                            "resource_directory", "use_only_state"),
                    attributeNames);
        } finally {
            client.close();
            client = null;
        }
    }

    // ==================================================================
    // Test 5: provider config demonstrably reaches Configure — the
    // provider's observable behaviour changes based on the config values
    // ==================================================================

    @Test
    @Order(5)
    @DisplayName("should change provider behaviour through Configure: state directory and forced create failures")
    void providerConfigReachesConfigure() throws Exception {
        assumeProviderAvailable();
        var provider = newBridgeProvider();

        try {
            provider.init();

            // Fresh directory so the file assertion below can only be satisfied
            // by tfcoremock honouring the resource_directory we configure now.
            var configuredDirectory = Files.createTempDirectory("kite-config-proof");
            var config = providerConfig(provider);
            config.put("resourceDirectory", configuredDirectory.toString());
            config.put("failOnCreate", java.util.List.of("fail-me"));
            provider.configure(config);

            var handler = (TerraformResourceTypeHandler) provider.<Map<String, Object>>getResourceType("SimpleResource");
            var attributeNames = simpleResourceAttributeNames();

            // Config value 1 (resource_directory): the created resource's state
            // file lands in the directory the config named.
            var createProps = propsWithNulls(attributeNames);
            createProps.put("id", "kite-config-proof");
            createProps.put("string", "configured");
            var created = handler.create(createProps, ResourceContext.empty());
            assertEquals("kite-config-proof", created.get("id"));
            assertTrue(Files.exists(configuredDirectory.resolve("kite-config-proof.json")),
                    "tfcoremock should persist the resource JSON in the configured resource_directory");

            // Config value 2 (fail_on_create): tfcoremock force-fails creates
            // for ids listed in the config.
            var failProps = propsWithNulls(attributeNames);
            failProps.put("id", "fail-me");
            var exception = assertThrows(RuntimeException.class,
                    () -> handler.create(failProps, ResourceContext.empty()));
            assertTrue(exception.getMessage().contains("forced failure"),
                    "fail_on_create should force a create failure, got: " + exception.getMessage());
        } finally {
            provider.stop();
            client = null;
        }
    }

    // ==================================================================
    // Test 6: invalid provider config is rejected naming the attribute
    // ==================================================================

    @Test
    @Order(6)
    @DisplayName("should reject a config attribute the provider schema does not define, naming it")
    void unknownProviderConfigAttributeIsRejected() throws Exception {
        assumeProviderAvailable();
        var provider = newBridgeProvider();

        try {
            provider.init();

            var config = providerConfig(provider);
            config.put("notARealAttribute", "some-value");

            var exception = assertThrows(IllegalArgumentException.class,
                    () -> provider.configure(config));
            assertTrue(exception.getMessage().contains("not_a_real_attribute"),
                    "rejection should name the offending attribute, got: " + exception.getMessage());
            assertTrue(exception.getMessage().contains("resource_directory"),
                    "rejection should list the valid attributes, got: " + exception.getMessage());
        } finally {
            provider.stop();
            client = null;
        }
    }

    // ==================================================================
    // Test 7: same-named resource and data source coexist in the registry
    // ==================================================================

    @Test
    @Order(7)
    @DisplayName("should register tfcoremock's mirrored resource and data source under distinct names")
    void sameNamedResourceAndDataSourceCoexist() throws Exception {
        assumeProviderAvailable();
        var provider = newBridgeProvider();

        try {
            provider.init();

            // tfcoremock mirrors every resource as a same-named data source —
            // assert that from the live schema rather than assuming it
            var schema = client.rpc().getProviderSchema();
            assertTrue(schema.resourceSchemas().containsKey("tfcoremock_simple_resource"),
                    "tfcoremock should expose tfcoremock_simple_resource as a resource");
            assertTrue(schema.dataSourceSchemas().containsKey("tfcoremock_simple_resource"),
                    "tfcoremock should expose tfcoremock_simple_resource as a data source, found: "
                            + schema.dataSourceSchemas().keySet());

            // Both are registered and addressable distinctly (kitecorp/kite-providers#13)
            assertInstanceOf(TerraformResourceTypeHandler.class, provider.getResourceType("SimpleResource"),
                    "The plain name must resolve to the resource handler");
            assertInstanceOf(TerraformDataSourceHandler.class, provider.getResourceType("SimpleResourceData"),
                    "The Data-suffixed name must resolve to the data source handler");

            // Schema DSL strings for both variants are preserved
            assertTrue(provider.getSchemaStrings().get("SimpleResource").startsWith("schema SimpleResource {"),
                    "Resource DSL should survive data source registration");
            assertTrue(provider.getSchemaStrings().get("SimpleResourceData").startsWith("schema SimpleResourceData {"),
                    "Data source DSL should be stored under the suffixed name");
        } finally {
            provider.stop();
            client = null;
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Builds a bridge provider around a fresh {@link GoPluginClient} for the
     * tfcoremock binary. The (String, GoPluginClient) constructor is
     * package-private, so it is invoked reflectively from this sub-package —
     * same pattern as the tfplugin5 integration suite.
     */
    private TerraformBridgeProvider newBridgeProvider() throws Exception {
        client = new GoPluginClient(providerBinaryPath);
        var constructor = TerraformBridgeProvider.class.getDeclaredConstructor(
                String.class, GoPluginClient.class);
        constructor.setAccessible(true);
        return constructor.newInstance("tfcoremock", client);
    }

    /**
     * Builds the camelCase provider config: every provider schema attribute
     * nulled, with {@code resource_directory} pointed at a temp dir so the
     * provider's JSON state files stay out of the repo tree.
     */
    private Map<String, Object> providerConfig(TerraformBridgeProvider provider) {
        var providerSchema = client.rpc().getProviderSchema().provider();
        assertNotNull(providerSchema, "tfcoremock should expose a provider config schema");

        var config = new LinkedHashMap<String, Object>();
        providerSchema.block().attributes()
                .forEach(attr -> config.put(TerraformPropertyMapper.toCamelCase(attr.name()), null));
        assertTrue(config.containsKey("resourceDirectory"),
                "tfcoremock provider config should have resource_directory, found: " + config.keySet());
        config.put("resourceDirectory", resourceDirectory.toString());
        return config;
    }

    /** snake_case attribute names of {@code tfcoremock_simple_resource}, from the live schema. */
    private java.util.List<String> simpleResourceAttributeNames() {
        return client.rpc().getProviderSchema()
                .resourceSchemas().get("tfcoremock_simple_resource")
                .block().attributes().stream()
                .map(cloud.kitelang.provider.terraform.TfAttribute::name)
                .toList();
    }

    /** camelCase property map with an explicit null for every schema attribute. */
    private Map<String, Object> propsWithNulls(java.util.List<String> snakeCaseAttributeNames) {
        var props = new LinkedHashMap<String, Object>();
        for (var attrName : snakeCaseAttributeNames) {
            props.put(TerraformPropertyMapper.toCamelCase(attrName), null);
        }
        return props;
    }
}
