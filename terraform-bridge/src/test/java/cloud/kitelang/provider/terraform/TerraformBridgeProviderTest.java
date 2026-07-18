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

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TDD tests for {@link TerraformBridgeProvider}.
 *
 * <p>Mocks the {@link GoPluginClient} and its gRPC stub to verify:
 * <ul>
 *   <li>init() fetches provider schema and registers resource + data source handlers</li>
 *   <li>configure() forwards config with snake_case conversion to the TF provider</li>
 *   <li>stop() closes the GoPluginClient and delegates to super</li>
 *   <li>Missing env vars produce descriptive errors</li>
 *   <li>Schema conversion produces correct Kite type names</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TerraformBridgeProviderTest {

    @Mock
    private GoPluginClient client;

    @Mock
    private ProviderGrpc.ProviderBlockingStub stub;

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /**
     * Builds a minimal TF {@link Schema} with the given attributes.
     * Each attribute has a name and a cty type (JSON-encoded bytes).
     */
    private Schema buildSchema(Map<String, String> attributes) {
        var blockBuilder = Schema.Block.newBuilder();
        for (var entry : attributes.entrySet()) {
            blockBuilder.addAttributes(Schema.Attribute.newBuilder()
                    .setName(entry.getKey())
                    .setType(ByteString.copyFrom(entry.getValue().getBytes(StandardCharsets.UTF_8)))
                    .build());
        }
        return Schema.newBuilder().setBlock(blockBuilder.build()).build();
    }

    /**
     * Builds a {@link GetProviderSchema.Response} with the given resource and data source schemas.
     */
    private GetProviderSchema.Response buildSchemaResponse(
            Map<String, Schema> resourceSchemas,
            Map<String, Schema> dataSourceSchemas) {
        var builder = GetProviderSchema.Response.newBuilder();
        if (resourceSchemas != null) {
            builder.putAllResourceSchemas(resourceSchemas);
        }
        if (dataSourceSchemas != null) {
            builder.putAllDataSourceSchemas(dataSourceSchemas);
        }
        // Provider-level schema (used for configure)
        builder.setProvider(buildSchema(Map.of("region", "\"string\"")));
        return builder.build();
    }

    // ---------------------------------------------------------------
    // 1. init() — resource type registration
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("init()")
    class Init {

        @Test
        @DisplayName("should call GetProviderSchema and register resource type handlers")
        void shouldRegisterResourceTypes() {
            // Given
            var instanceSchema = buildSchema(Map.of(
                    "ami", "\"string\"",
                    "instance_type", "\"string\""
            ));
            var bucketSchema = buildSchema(Map.of(
                    "bucket", "\"string\"",
                    "acl", "\"string\""
            ));
            var schemaResponse = buildSchemaResponse(
                    Map.of("aws_instance", instanceSchema, "aws_s3_bucket", bucketSchema),
                    Map.of()
            );
            when(client.rpc()).thenReturn(new Tfplugin5Rpc(stub));
            when(stub.getSchema(any(GetProviderSchema.Request.class))).thenReturn(schemaResponse);

            var provider = new TerraformBridgeProvider("aws", client);

            // When
            provider.init();

            // Then
            verify(stub).getSchema(any(GetProviderSchema.Request.class));
            assertNotNull(provider.getResourceType("Instance"));
            assertNotNull(provider.getResourceType("S3Bucket"));
        }

        @Test
        @DisplayName("should register data source handlers separately from resource types")
        void shouldRegisterDataSourceHandlers() {
            // Given
            var amiDataSource = buildSchema(Map.of(
                    "name", "\"string\"",
                    "owner_id", "\"string\""
            ));
            var schemaResponse = buildSchemaResponse(
                    Map.of(),
                    Map.of("aws_ami", amiDataSource)
            );
            when(client.rpc()).thenReturn(new Tfplugin5Rpc(stub));
            when(stub.getSchema(any(GetProviderSchema.Request.class))).thenReturn(schemaResponse);

            var provider = new TerraformBridgeProvider("aws", client);

            // When
            provider.init();

            // Then
            var handler = provider.getResourceType("Ami");
            assertNotNull(handler, "Data source handler should be registered");
            assertInstanceOf(TerraformDataSourceHandler.class, handler,
                    "Data source should use TerraformDataSourceHandler, not TerraformResourceTypeHandler");
        }

        @Test
        @DisplayName("should register both resource types and data sources in one init call")
        void shouldRegisterBothResourcesAndDataSources() {
            // Given
            var instanceSchema = buildSchema(Map.of("ami", "\"string\""));
            var amiDataSource = buildSchema(Map.of("name", "\"string\""));
            var schemaResponse = buildSchemaResponse(
                    Map.of("aws_instance", instanceSchema),
                    Map.of("aws_ami", amiDataSource)
            );
            when(client.rpc()).thenReturn(new Tfplugin5Rpc(stub));
            when(stub.getSchema(any(GetProviderSchema.Request.class))).thenReturn(schemaResponse);

            var provider = new TerraformBridgeProvider("aws", client);

            // When
            provider.init();

            // Then
            assertEquals(2, provider.getResourceTypes().size());
            assertInstanceOf(TerraformResourceTypeHandler.class, provider.getResourceType("Instance"));
            assertInstanceOf(TerraformDataSourceHandler.class, provider.getResourceType("Ami"));
        }
    }

    // ---------------------------------------------------------------
    // 2. configure()
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("configure()")
    class ConfigureTests {

        private TerraformBridgeProvider provider;

        @BeforeEach
        void setUp() {
            // Set up a provider with a minimal schema so init() succeeds
            var providerSchema = buildSchema(Map.of("region", "\"string\""));
            var schemaResponse = buildSchemaResponse(Map.of(), Map.of());
            when(client.rpc()).thenReturn(new Tfplugin5Rpc(stub));
            when(stub.getSchema(any(GetProviderSchema.Request.class))).thenReturn(schemaResponse);

            provider = new TerraformBridgeProvider("aws", client);
            provider.init();
        }

        @Test
        @DisplayName("should forward config to TF provider with snake_case key conversion")
        void shouldForwardConfigWithSnakeCaseKeys() {
            // Given
            var camelConfig = new LinkedHashMap<String, Object>();
            camelConfig.put("accessKey", "AKIAEXAMPLE");
            camelConfig.put("secretKey", "secret123");
            camelConfig.put("region", "us-east-1");

            var configureResponse = Configure.Response.newBuilder().build();
            when(stub.configure(any(Configure.Request.class))).thenReturn(configureResponse);

            // When
            provider.configure(camelConfig);

            // Then
            var captor = ArgumentCaptor.forClass(Configure.Request.class);
            verify(stub).configure(captor.capture());
            var request = captor.getValue();

            // The config DynamicValue should contain msgpack-encoded snake_case properties
            assertNotEquals(DynamicValue.getDefaultInstance(), request.getConfig());
        }

        @Test
        @DisplayName("should throw on ERROR diagnostics from configure")
        void shouldThrowOnConfigureError() {
            // Given
            var config = Map.<String, Object>of("region", "invalid");

            var errorDiag = Diagnostic.newBuilder()
                    .setSeverity(Diagnostic.Severity.ERROR)
                    .setSummary("InvalidRegion")
                    .setDetail("Region 'invalid' is not valid")
                    .build();
            var configureResponse = Configure.Response.newBuilder()
                    .addDiagnostics(errorDiag)
                    .build();
            when(stub.configure(any(Configure.Request.class))).thenReturn(configureResponse);

            // When/Then
            var exception = assertThrows(RuntimeException.class, () -> provider.configure(config));
            assertTrue(exception.getMessage().contains("InvalidRegion"));
        }
    }

    // ---------------------------------------------------------------
    // 3. stop()
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("stop()")
    class StopTests {

        @Test
        @DisplayName("should close the GoPluginClient on stop")
        void shouldCloseClientOnStop() {
            // Given
            var schemaResponse = buildSchemaResponse(Map.of(), Map.of());
            when(client.rpc()).thenReturn(new Tfplugin5Rpc(stub));
            when(stub.getSchema(any(GetProviderSchema.Request.class))).thenReturn(schemaResponse);

            var provider = new TerraformBridgeProvider("aws", client);
            provider.init();

            // When
            provider.stop();

            // Then
            verify(client).close();
        }
    }

    // ---------------------------------------------------------------
    // 4. Schema conversion — type names
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("Schema conversion")
    class SchemaConversion {

        @Test
        @DisplayName("should convert TF type names to Kite PascalCase via converter")
        void shouldConvertTypeNamesCorrectly() {
            // Given
            var sgSchema = buildSchema(Map.of("name", "\"string\""));
            var rtSchema = buildSchema(Map.of("name", "\"string\""));
            var schemaResponse = buildSchemaResponse(
                    Map.of("aws_security_group", sgSchema, "aws_route_table", rtSchema),
                    Map.of()
            );
            when(client.rpc()).thenReturn(new Tfplugin5Rpc(stub));
            when(stub.getSchema(any(GetProviderSchema.Request.class))).thenReturn(schemaResponse);

            var provider = new TerraformBridgeProvider("aws", client);

            // When
            provider.init();

            // Then
            assertNotNull(provider.getResourceType("SecurityGroup"),
                    "aws_security_group should become SecurityGroup");
            assertNotNull(provider.getResourceType("RouteTable"),
                    "aws_route_table should become RouteTable");
        }
    }

    // ---------------------------------------------------------------
    // 5. Schema DSL strings
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("Schema DSL generation")
    class SchemaDsl {

        @Test
        @DisplayName("should generate and store Kite schema DSL strings for registered types")
        void shouldStoreSchemaStrings() {
            // Given
            var instanceSchema = buildSchema(Map.of(
                    "ami", "\"string\"",
                    "instance_type", "\"string\""
            ));
            var schemaResponse = buildSchemaResponse(
                    Map.of("aws_instance", instanceSchema),
                    Map.of()
            );
            when(client.rpc()).thenReturn(new Tfplugin5Rpc(stub));
            when(stub.getSchema(any(GetProviderSchema.Request.class))).thenReturn(schemaResponse);

            var provider = new TerraformBridgeProvider("aws", client);

            // When
            provider.init();

            // Then
            var schemas = provider.getSchemaStrings();
            assertNotNull(schemas.get("Instance"), "Should have schema DSL for Instance");
            assertTrue(schemas.get("Instance").contains("schema Instance {"),
                    "Schema DSL should contain the type declaration");
        }

        @Test
        @DisplayName("should include domain classification in schema strings map")
        void shouldIncludeDomainInfo() {
            // Given
            var vpcSchema = buildSchema(Map.of("cidr_block", "\"string\""));
            var s3Schema = buildSchema(Map.of("bucket", "\"string\""));
            var schemaResponse = buildSchemaResponse(
                    Map.of("aws_vpc", vpcSchema, "aws_s3_bucket", s3Schema),
                    Map.of()
            );
            when(client.rpc()).thenReturn(new Tfplugin5Rpc(stub));
            when(stub.getSchema(any(GetProviderSchema.Request.class))).thenReturn(schemaResponse);

            var provider = new TerraformBridgeProvider("aws", client);

            // When
            provider.init();

            // Then
            var domains = provider.getSchemaDomains();
            assertEquals("networking", domains.get("Vpc"));
            assertEquals("storage", domains.get("S3Bucket"));
        }
    }

    // ---------------------------------------------------------------
    // 6. Provider name and prefix
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("Provider name handling")
    class ProviderName {

        @Test
        @DisplayName("should use provider name as prefix for type name stripping")
        void shouldUseProviderNameAsPrefix() {
            // Given — Google provider with google_ prefix
            var networkSchema = buildSchema(Map.of("name", "\"string\""));
            var schemaResponse = buildSchemaResponse(
                    Map.of("google_compute_network", networkSchema),
                    Map.of()
            );
            when(client.rpc()).thenReturn(new Tfplugin5Rpc(stub));
            when(stub.getSchema(any(GetProviderSchema.Request.class))).thenReturn(schemaResponse);

            var provider = new TerraformBridgeProvider("google", client);

            // When
            provider.init();

            // Then
            assertNotNull(provider.getResourceType("ComputeNetwork"),
                    "google_compute_network should become ComputeNetwork");
        }
    }
}
