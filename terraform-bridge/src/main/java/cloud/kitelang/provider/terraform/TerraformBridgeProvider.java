package cloud.kitelang.provider.terraform;
import cloud.kitelang.tfplugin.CtyCodec;
import cloud.kitelang.tfplugin.GoPluginClient;
import cloud.kitelang.tfplugin.TfAttribute;
import cloud.kitelang.tfplugin.TfBlock;
import cloud.kitelang.tfplugin.TfDiagnostic;
import cloud.kitelang.tfplugin.TfNestedBlock;
import cloud.kitelang.tfplugin.TfSchema;
import cloud.kitelang.tfplugin.Tfplugin6Rpc;

import cloud.kitelang.provider.KiteProvider;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** Cty object type of a provider without any config schema block. */
    private static final String EMPTY_OBJECT_TYPE = "[\"object\",{}]";

    /**
     * JSON-encoded cty object type for the provider config schema (used in
     * configure()). Null until {@link #init()} runs — configure() guards on
     * this to fail with a lifecycle error instead of an NPE (kitecorp/kite#32).
     */
    private String providerConfigSchemaType;

    /** snake_case attribute names of the provider config schema (used in configure()). */
    private Set<String> providerConfigAttributeNames = Set.of();

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

        // A provider can answer GetProviderSchema with error diagnostics
        // INSTEAD of schemas (e.g. tfcoremock rejecting its dynamic resources
        // file). Continuing would silently register zero types — fail loudly
        // with the provider's own explanation instead (kitecorp/kite-providers#6).
        if (providerSchema.hasErrors()) {
            var details = providerSchema.diagnostics().stream()
                    .filter(TfDiagnostic::isError)
                    .map(diagnostic -> diagnostic.summary()
                            + (diagnostic.detail() == null || diagnostic.detail().isBlank()
                               ? "" : ": " + diagnostic.detail()))
                    .collect(java.util.stream.Collectors.joining("; "));
            throw new IllegalStateException(
                    "Terraform provider '%s' failed GetProviderSchema: %s".formatted(providerName, details));
        }

        // Build converter with the provider name as prefix
        this.converter = new TerraformSchemaConverter(providerName);

        // Build the provider-level config schema type (used in configure()).
        // Providers without a config schema still get Configure — Terraform
        // core always calls it (env-var-based auth happens there) — so the
        // absent-schema case is an empty object, not a skipped RPC.
        if (providerSchema.provider() != null) {
            this.providerConfigSchemaType = buildObjectType(providerSchema.provider().block());
            this.providerConfigAttributeNames = providerSchema.provider().block().attributes().stream()
                    .map(TfAttribute::name)
                    .collect(Collectors.toUnmodifiableSet());
        } else {
            this.providerConfigSchemaType = EMPTY_OBJECT_TYPE;
            this.providerConfigAttributeNames = Set.of();
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
     * <p>Converts camelCase config keys to snake_case, rejects keys that are not
     * in the provider's config schema (silently dropping them would configure the
     * provider with different settings than the user wrote), encodes as cty
     * msgpack, validates via the protocol's provider-config validation RPC, and
     * sends the validated config to the {@code Configure} RPC.</p>
     *
     * @param configuration a {@code Map<String, Object>} with provider configuration,
     *                      or null for an empty configuration
     * @throws IllegalArgumentException if a config key is not a provider config attribute
     * @throws RuntimeException         if the TF provider returns ERROR diagnostics
     */
    @Override
    @SuppressWarnings("unchecked")
    public void configure(Object configuration) {
        super.configure(configuration);

        if (providerConfigSchemaType == null) {
            throw new IllegalStateException("init() must be called before configure()");
        }

        var configMap = configuration != null
                ? (Map<String, Object>) configuration
                : Map.<String, Object>of();
        var snakeCaseConfig = TerraformPropertyMapper.toSnakeCase(configMap);
        rejectUnknownAttributes(snakeCaseConfig.keySet());

        log.debug("Configuring TF provider '{}' with {} properties", providerName, snakeCaseConfig.size());

        // Encode config to cty msgpack using the provider schema type
        var codec = new CtyCodec();
        var encoded = codec.encode(snakeCaseConfig, providerConfigSchemaType);

        // Terraform core validates provider config before configuring; on
        // tfplugin5 the validation additionally rewrites the config (defaults),
        // so Configure must receive the prepared bytes.
        var validation = client.rpc().validateProviderConfig(encoded);
        TerraformDiagnostics.check(validation.diagnostics(), "provider config validation", providerName);

        var diagnostics = client.rpc().configure(validation.preparedConfig());
        TerraformDiagnostics.check(diagnostics, "configure", providerName);

        log.info("TF provider '{}' configured successfully", providerName);
    }

    /**
     * Rejects config keys that do not exist in the provider's config schema,
     * naming the offending attribute(s) — mirrors Terraform core's
     * "Unsupported argument" check, which also happens client-side.
     */
    private void rejectUnknownAttributes(Set<String> snakeCaseKeys) {
        var unknown = snakeCaseKeys.stream()
                .filter(key -> !providerConfigAttributeNames.contains(key))
                .sorted()
                .toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unknown provider configuration attribute(s) %s for provider '%s'. Valid attributes: %s"
                            .formatted(unknown, providerName,
                                    providerConfigAttributeNames.stream().sorted().toList()));
        }
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
                TerraformResourceTypeHandler.readOnlyAttributeNames(schema.block()), schema.version(),
                converter.toApiSchema(kiteTypeName, schema));
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
     *
     * <p>Terraform keeps resources and data sources in separate namespaces and
     * providers commonly expose both under one TF type name ({@code aws_instance},
     * every {@code tfcoremock_*} type). Kite's registry is keyed by a single flat
     * type name, so data sources register under the deterministic {@code Data}
     * suffix ({@code aws_ami} -> {@code AmiData}) to coexist with the same-named
     * resource (kitecorp/kite-providers#13). A residual clash — a genuine resource
     * whose converted name already ends in the suffixed name — fails init loudly
     * rather than silently overwriting either handler.</p>
     */
    private void registerDataSourceHandler(String tfTypeName, TfSchema schema) {
        var kiteTypeName = converter.toKiteDataSourceTypeName(tfTypeName);
        if (getResourceType(kiteTypeName) != null) {
            throw new IllegalStateException(
                    "Data source '%s' maps to Kite type name '%s', which is already registered for a resource type. "
                            .formatted(tfTypeName, kiteTypeName)
                    + "Resource and data source names must stay distinct after the 'Data' suffix is applied.");
        }
        var schemaTypeJson = buildObjectType(schema.block());

        var handler = new TerraformDataSourceHandler(tfTypeName, kiteTypeName, client, schemaTypeJson,
                TerraformResourceTypeHandler.readOnlyAttributeNames(schema.block()),
                converter.toApiSchema(kiteTypeName, schema));
        registerResource(kiteTypeName, handler);

        // Store schema DSL and domain for the schema registry, keyed by the
        // suffixed name so the resource variant's entries are preserved
        schemaStrings.put(kiteTypeName, converter.toKiteSchemaNamed(kiteTypeName, schema));
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
     * <p>The result feeds {@link CtyCodec} to encode/decode a resource's property
     * values, so it MUST mirror Terraform's own {@code Block.ImpliedType()}: the
     * object's fields are the block's attributes <em>and</em> its nested blocks.
     * Dropping nested blocks left the cty type missing the block keys the provider
     * always includes in the wire object, so {@code CtyCodec.decode} threw on the
     * first such key — breaking most real AWS/GCP resources (kitecorp/kite#25).</p>
     *
     * <p>Each field's cty type:
     * <ul>
     *   <li>attribute &rarr; its {@code typeJson} (proto6 {@code nested_type} is
     *       already synthesised into that JSON by {@link Tfplugin6Rpc}, so nested
     *       attributes need no special handling here)</li>
     *   <li>nested block SINGLE &rarr; the nested object type</li>
     *   <li>nested block LIST/SET/MAP &rarr; {@code ["list"|"set"|"map", objectType]}</li>
     *   <li>nested block GROUP &rarr; its attributes flattened into this object,
     *       matching the DSL/api-schema rendering ({@code appendNestedBlock},
     *       {@code collectApiProperties}) so all three representations describe the
     *       same property set</li>
     * </ul>
     *
     * <p>Package-private and static so the synthesised JSON can be asserted
     * directly; it depends only on its {@code block} argument.</p>
     *
     * @param block the TF schema block
     * @return JSON string representing the cty object type
     */
    static String buildObjectType(TfBlock block) {
        var fields = new ArrayList<String>();
        collectFieldTypes(block, fields);
        return "[\"object\",{" + String.join(",", fields) + "}]";
    }

    /**
     * Appends {@code "name":ctyType} entries for a block's attributes and nested
     * blocks, recursing so a GROUP block's attributes flatten into the parent.
     * Attribute and block names are provider-supplied {@code [a-z0-9_]}, so no
     * JSON escaping is needed (the same assumption the cty type bytes rely on).
     */
    private static void collectFieldTypes(TfBlock block, List<String> fields) {
        for (var attr : block.attributes()) {
            fields.add('"' + attr.name() + "\":" + attr.typeJson());
        }
        for (var nested : block.blockTypes()) {
            if (nested.nesting() == TfNestedBlock.Nesting.GROUP) {
                collectFieldTypes(nested.block(), fields);
            } else {
                fields.add('"' + nested.typeName() + "\":" + nestedBlockType(nested));
            }
        }
    }

    /**
     * Wraps a nested block's object type per its nesting mode, mirroring the cty
     * implied type of a Terraform nested block. GROUP is handled by the caller
     * (flattened); INVALID cannot occur for a well-formed schema and falls back to
     * the bare object rather than emitting invalid JSON.
     */
    private static String nestedBlockType(TfNestedBlock nested) {
        var objectType = buildObjectType(nested.block());
        return switch (nested.nesting()) {
            case LIST -> "[\"list\"," + objectType + "]";
            case SET -> "[\"set\"," + objectType + "]";
            case MAP -> "[\"map\"," + objectType + "]";
            default -> objectType; // SINGLE (and defensively INVALID)
        };
    }
}
