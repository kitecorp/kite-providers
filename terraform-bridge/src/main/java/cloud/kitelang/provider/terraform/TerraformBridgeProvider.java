package cloud.kitelang.provider.terraform;

import cloud.kitelang.provider.KiteProvider;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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

    /**
     * Entry point for the bridge process. Lives on the provider class itself —
     * the kite-provider-gradle-plugin auto-detects the launcher main class by
     * finding the {@link KiteProvider} subclass, so a separate entry-point
     * class would produce a start script pointing at a class without a main.
     */
    public static void main(String[] args) throws Exception {
        log.info("Starting Terraform Bridge Provider...");
        var provider = new TerraformBridgeProvider();
        provider.init();
        cloud.kitelang.provider.ProviderServer.serve(provider);
    }

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

        // Fetch the full provider schema via the version-agnostic facade
        var providerSchema = client.rpc().getProviderSchema();

        // Build converter with the provider name as prefix
        this.converter = new TerraformSchemaConverter(providerName);

        // Build the provider-level config schema type (used in configure())
        if (providerSchema.provider() != null) {
            this.providerConfigSchemaType = buildObjectType(providerSchema.provider().block());
        }

        // Register resource types
        var resourceCount = 0;
        for (var entry : providerSchema.resourceSchemas().entrySet()) {
            registerResourceHandler(entry.getKey(), entry.getValue());
            resourceCount++;
        }

        // Register data source types
        var dataSourceCount = 0;
        for (var entry : providerSchema.dataSourceSchemas().entrySet()) {
            registerDataSourceHandler(entry.getKey(), entry.getValue());
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

        var diagnostics = client.rpc().configure(encoded);
        TerraformDiagnostics.check(diagnostics, "configure", providerName);

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
    private void registerResourceHandler(String tfTypeName, TfSchema schema) {
        var kiteTypeName = converter.toKiteTypeName(tfTypeName);
        var schemaTypeJson = buildObjectType(schema.block());

        var handler = new TerraformResourceTypeHandler(tfTypeName, kiteTypeName, client, schemaTypeJson,
                TerraformResourceTypeHandler.readOnlyAttributeNames(schema.block()));
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
    private void registerDataSourceHandler(String tfTypeName, TfSchema schema) {
        var kiteTypeName = converter.toKiteTypeName(tfTypeName);
        var schemaTypeJson = buildObjectType(schema.block());

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
     * <p>Each attribute's {@code typeJson} is already valid JSON (e.g., {@code "string"} or
     * {@code ["list", "string"]}), so it is embedded as a raw JSON value rather than
     * string-escaped.</p>
     *
     * @param block the TF schema block containing attribute definitions
     * @return JSON string representing the cty object type
     */
    private String buildObjectType(TfBlock block) {
        var sb = new StringBuilder("[\"object\",{");
        var first = true;
        for (var attr : block.attributes()) {
            if (!first) {
                sb.append(',');
            }
            // Key: attribute name (schema names are [a-z0-9_], no escaping needed)
            sb.append('"').append(attr.name()).append("\":");
            // Value: raw JSON cty type
            sb.append(attr.typeJson());
            first = false;
        }
        sb.append("}]");
        return sb.toString();
    }
}
