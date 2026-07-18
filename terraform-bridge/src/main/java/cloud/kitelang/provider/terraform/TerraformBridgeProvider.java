package cloud.kitelang.provider.terraform;

import cloud.kitelang.provider.KiteProvider;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import tfplugin5.Tfplugin5.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Terraform Bridge Provider for Kite.
 *
 * <p>Wraps native Terraform providers (tfplugin5 / tfplugin6) and translates
 * between Kite's gRPC protocol and the Terraform plugin protocol, allowing
 * any existing Terraform provider to be used as a Kite provider.</p>
 *
 * <p>Auto-discovery is disabled because resource types are dynamically
 * populated at runtime from the wrapped Terraform provider's schema.</p>
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>Constructor — reads env vars, creates GoPluginClient (or accepts a pre-built one for tests)</li>
 *   <li>{@link #init()} — fetches TF schema, registers resource and data source handlers</li>
 *   <li>{@link #configure(Object)} — forwards provider config to the TF binary</li>
 *   <li>{@link #stop()} — shuts down the GoPluginClient and the provider process</li>
 * </ol>
 *
 * @see GoPluginClient
 * @see TerraformSchemaConverter
 * @see TerraformResourceTypeHandler
 * @see TerraformDataSourceHandler
 */
@Slf4j
public class TerraformBridgeProvider extends KiteProvider {

    /** Environment variable for the path to the Terraform provider binary. */
    static final String ENV_PROVIDER_PATH = "KITE_TF_PROVIDER_PATH";

    /** Environment variable for the Terraform provider name (e.g., "aws", "google"). */
    static final String ENV_PROVIDER_NAME = "KITE_TF_PROVIDER_NAME";

    private final GoPluginClient client;
    private final String providerName;
    private TerraformSchemaConverter converter;

    /** Kite schema DSL strings keyed by Kite type name (e.g., "Instance" -> "schema Instance { ... }"). */
    private final Map<String, String> schemaStrings = new HashMap<>();

    /** Domain classification keyed by Kite type name (e.g., "Vpc" -> "networking"). */
    private final Map<String, String> schemaDomains = new HashMap<>();

    /** JSON-encoded cty object type for the provider config schema (used in configure()). */
    private String providerConfigSchemaType;

    /**
     * Creates the bridge provider from environment variables.
     *
     * <p>Reads {@code KITE_TF_PROVIDER_PATH} and {@code KITE_TF_PROVIDER_NAME} from the
     * environment. If either is missing, throws a descriptive {@link IllegalStateException}.</p>
     *
     * @throws IllegalStateException if required environment variables are not set
     */
    public TerraformBridgeProvider() {
        super(false); // disable auto-discovery; resources come from Terraform schema

        var binaryPath = System.getenv(ENV_PROVIDER_PATH);
        if (binaryPath == null || binaryPath.isBlank()) {
            throw new IllegalStateException(
                    "Environment variable %s is required but not set. ".formatted(ENV_PROVIDER_PATH)
                    + "Set it to the path of the Terraform provider binary "
                    + "(e.g., /usr/local/bin/terraform-provider-aws).");
        }

        this.providerName = System.getenv(ENV_PROVIDER_NAME);
        if (providerName == null || providerName.isBlank()) {
            throw new IllegalStateException(
                    "Environment variable %s is required but not set. ".formatted(ENV_PROVIDER_NAME)
                    + "Set it to the provider name (e.g., \"aws\", \"google\").");
        }

        this.client = new GoPluginClient(Path.of(binaryPath));
        log.info("Terraform Bridge Provider created: provider={}, binary={}", providerName, binaryPath);
    }

    /**
     * Creates the bridge provider with an externally supplied client and provider name.
     *
     * <p>Used for testing, where we inject a mocked {@link GoPluginClient} instead of
     * launching a real subprocess.</p>
     *
     * @param providerName the TF provider name (e.g., "aws")
     * @param client       the pre-built (or mocked) GoPluginClient
     */
    TerraformBridgeProvider(String providerName, GoPluginClient client) {
        super(false);
        this.providerName = providerName;
        this.client = client;
        log.info("Terraform Bridge Provider created (test mode): provider={}", providerName);
    }

    /**
     * Initialises the bridge by fetching the TF provider schema and registering handlers.
     *
     * <p>Must be called after construction and before serving requests. Performs:
     * <ol>
     *   <li>RPC call to {@code GetSchema} on the TF provider</li>
     *   <li>Registration of a {@link TerraformResourceTypeHandler} for each resource type</li>
     *   <li>Registration of a {@link TerraformDataSourceHandler} for each data source</li>
     *   <li>Storage of Kite schema DSL strings for the schema registry</li>
     * </ol>
     */
    public void init() {
        log.info("Initialising Terraform Bridge Provider: fetching schema from '{}'", providerName);
        var blockingStub = client.getStub();

        // Fetch the full provider schema
        var schemaRequest = GetProviderSchema.Request.getDefaultInstance();
        var schemaResponse = blockingStub.getSchema(schemaRequest);

        // Build converter with the provider name as prefix
        this.converter = new TerraformSchemaConverter(providerName);

        // Build the provider-level config schema type (used in configure())
        if (schemaResponse.hasProvider()) {
            this.providerConfigSchemaType = buildObjectType(schemaResponse.getProvider().getBlock());
        }

        // Register resource types
        var resourceCount = 0;
        for (var entry : schemaResponse.getResourceSchemasMap().entrySet()) {
            var tfTypeName = entry.getKey();
            var schema = entry.getValue();
            registerResourceHandler(tfTypeName, schema);
            resourceCount++;
        }

        // Register data source types
        var dataSourceCount = 0;
        for (var entry : schemaResponse.getDataSourceSchemasMap().entrySet()) {
            var tfTypeName = entry.getKey();
            var schema = entry.getValue();
            registerDataSourceHandler(tfTypeName, schema);
            dataSourceCount++;
        }

        log.info("Terraform Bridge Provider initialised: {} resource types, {} data sources",
                resourceCount, dataSourceCount);
    }

    /**
     * Configures the TF provider with runtime credentials/settings.
     *
     * <p>Converts camelCase config keys to snake_case, encodes as cty msgpack,
     * and sends the {@code Configure} RPC to the TF provider.</p>
     *
     * @param configuration a {@code Map<String, Object>} with provider configuration
     * @throws RuntimeException if the TF provider returns ERROR diagnostics
     */
    @Override
    @SuppressWarnings("unchecked")
    public void configure(Object configuration) {
        super.configure(configuration);

        var configMap = (Map<String, Object>) configuration;
        var snakeCaseConfig = TerraformPropertyMapper.toSnakeCase(configMap);

        log.debug("Configuring TF provider '{}' with {} properties", providerName, snakeCaseConfig.size());

        // Encode config to cty msgpack using the provider schema type
        var codec = new CtyCodec();
        var encoded = codec.encode(snakeCaseConfig, providerConfigSchemaType);
        var dynamicValue = DynamicValue.newBuilder()
                .setMsgpack(ByteString.copyFrom(encoded))
                .build();

        var request = Configure.Request.newBuilder()
                .setConfig(dynamicValue)
                .build();

        var response = client.getStub().configure(request);
        checkDiagnostics(response.getDiagnosticsList(), "configure");

        log.info("TF provider '{}' configured successfully", providerName);
    }

    /**
     * Shuts down the TF provider process and releases gRPC resources.
     */
    @Override
    public void stop() {
        log.info("Stopping Terraform Bridge Provider '{}'", providerName);
        client.close();
        super.stop();
    }

    /**
     * Returns the Kite schema DSL strings for all registered types.
     *
     * <p>Keyed by Kite type name (e.g., "Instance" -> "schema Instance { ... }").
     * Used by the engine's {@code ProviderSchemaRegistry} for import resolution.</p>
     *
     * @return unmodifiable map of type name to schema DSL string
     */
    public Map<String, String> getSchemaStrings() {
        return Collections.unmodifiableMap(schemaStrings);
    }

    /**
     * Returns the domain classification for all registered types.
     *
     * <p>Keyed by Kite type name (e.g., "Vpc" -> "networking", "S3Bucket" -> "storage").
     * Used for organising types into import domains.</p>
     *
     * @return unmodifiable map of type name to domain
     */
    public Map<String, String> getSchemaDomains() {
        return Collections.unmodifiableMap(schemaDomains);
    }

    // ---------------------------------------------------------------
    // Internal: handler registration
    // ---------------------------------------------------------------

    /**
     * Registers a resource type handler and stores its schema DSL.
     */
    private void registerResourceHandler(String tfTypeName, Schema schema) {
        var kiteTypeName = converter.toKiteTypeName(tfTypeName);
        var schemaTypeJson = buildObjectType(schema.getBlock());

        var handler = new TerraformResourceTypeHandler(tfTypeName, kiteTypeName, client, schemaTypeJson,
                TerraformResourceTypeHandler.readOnlyAttributeNames(schema.getBlock()));
        registerResource(kiteTypeName, handler);

        // Store schema DSL and domain for the schema registry
        var schemaDsl = converter.toKiteSchema(tfTypeName, schema);
        schemaStrings.put(kiteTypeName, schemaDsl);
        schemaDomains.put(kiteTypeName, converter.getDomain(tfTypeName));

        log.debug("Registered resource type: {} -> {} (domain: {})",
                tfTypeName, kiteTypeName, schemaDomains.get(kiteTypeName));
    }

    /**
     * Registers a data source handler and stores its schema DSL.
     */
    private void registerDataSourceHandler(String tfTypeName, Schema schema) {
        var kiteTypeName = converter.toKiteTypeName(tfTypeName);
        var schemaTypeJson = buildObjectType(schema.getBlock());

        var handler = new TerraformDataSourceHandler(tfTypeName, kiteTypeName, client, schemaTypeJson);
        registerResource(kiteTypeName, handler);

        // Store schema DSL and domain for the schema registry
        var schemaDsl = converter.toKiteSchema(tfTypeName, schema);
        schemaStrings.put(kiteTypeName, schemaDsl);
        schemaDomains.put(kiteTypeName, converter.getDomain(tfTypeName));

        log.debug("Registered data source: {} -> {} (domain: {})",
                tfTypeName, kiteTypeName, schemaDomains.get(kiteTypeName));
    }

    // ---------------------------------------------------------------
    // Internal: cty object type building
    // ---------------------------------------------------------------

    /**
     * Builds a JSON-encoded cty object type string from a TF schema block.
     *
     * <p>The result is used by {@link CtyCodec} to correctly encode/decode property values.
     * Format: {@code ["object", {"ami": "string", "instance_type": "string", ...}]}</p>
     *
     * <p>Each attribute's type bytes are already valid JSON (e.g., {@code "string"} or
     * {@code ["list", "string"]}), so they are embedded as raw JSON values rather than
     * string-escaped.</p>
     *
     * @param block the TF schema block containing attribute definitions
     * @return JSON string representing the cty object type
     */
    private String buildObjectType(Schema.Block block) {
        var sb = new StringBuilder("[\"object\",{");
        var first = true;
        for (var attr : block.getAttributesList()) {
            if (!first) {
                sb.append(',');
            }
            // Key: JSON-escaped attribute name
            sb.append('"').append(attr.getName()).append("\":");
            // Value: raw JSON cty type (already valid JSON from protobuf bytes)
            sb.append(new String(attr.getType().toByteArray(), StandardCharsets.UTF_8));
            first = false;
        }
        sb.append("}]");
        return sb.toString();
    }

    // ---------------------------------------------------------------
    // Internal: diagnostics check (reuse pattern from AbstractTerraformHandler)
    // ---------------------------------------------------------------

    /**
     * Checks TF diagnostics for ERROR-level entries and throws if any are found.
     *
     * @param diagnostics the diagnostics list from a TF response
     * @param operation   the operation name for error reporting
     * @throws RuntimeException if any ERROR-level diagnostic is present
     */
    private void checkDiagnostics(List<Diagnostic> diagnostics, String operation) {
        diagnostics.stream()
                .filter(d -> d.getSeverity() == Diagnostic.Severity.WARNING)
                .forEach(d -> log.warn("Terraform {} warning: {} — {}",
                        operation, d.getSummary(), d.getDetail()));

        var errors = diagnostics.stream()
                .filter(d -> d.getSeverity() == Diagnostic.Severity.ERROR)
                .toList();

        if (!errors.isEmpty()) {
            var message = errors.stream()
                    .map(d -> d.getSummary() + ": " + d.getDetail())
                    .collect(Collectors.joining("; "));
            throw new RuntimeException("Terraform %s failed: %s".formatted(operation, message));
        }
    }
}
