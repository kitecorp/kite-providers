package cloud.kitelang.provider.terraform;

import cloud.kitelang.api.resource.Property;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts Terraform provider schemas (from {@code GetProviderSchema} gRPC responses)
 * into Kite schema DSL strings for registration with {@code ProviderSchemaRegistry}.
 *
 * <p>This is the translation layer between Terraform's schema model (cty types,
 * snake_case names, required/optional/computed flags) and Kite's type system
 * (PascalCase types, camelCase properties, {@code @cloud}/{@code @sensitive} decorators).
 * It consumes the protocol-neutral {@link TfSchema} model, so schemas from
 * tfplugin5 and tfplugin6 providers convert identically.</p>
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Convert TF resource type names to Kite PascalCase type names</li>
 *   <li>Parse cty type constraint bytes into Kite type strings</li>
 *   <li>Map TF attribute flags to Kite property decorators</li>
 *   <li>Handle nested blocks (SINGLE, LIST, SET, MAP, GROUP)</li>
 *   <li>Classify TF types into Kite import domains via heuristics</li>
 * </ul>
 *
 * <p>This class is immutable and thread-safe after construction.</p>
 *
 * @see TerraformPropertyMapper for snake_case/camelCase property name conversions
 */
public class TerraformSchemaConverter {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Domain classification rules ordered from most-specific to least-specific.
     *
     * <p>More specific patterns (e.g. {@code _db_}, {@code _iam_}) are checked before
     * broad patterns (e.g. {@code _instance}) to avoid misclassification. For example,
     * {@code aws_db_instance} should match "database" via {@code _db_}, not "compute"
     * via {@code _instance}.</p>
     */
    private static final List<Map.Entry<String, List<String>>> DOMAIN_RULES = List.of(
            Map.entry("database", List.of("_rds_", "_db_", "_dynamodb")),
            Map.entry("iam", List.of("_iam_")),
            Map.entry("serverless", List.of("_lambda_", "_function")),
            Map.entry("storage", List.of("_s3_bucket", "_s3_object", "_storage", "_blob")),
            Map.entry("loadbalancing", List.of("_lb", "_load_balancer", "_target_group")),
            Map.entry("networking", List.of("_vpc", "_subnet", "_security_group", "_route_table",
                    "_route53", "_network")),
            Map.entry("compute", List.of("_instance", "_launch_template", "_autoscaling")),
            Map.entry("dns", List.of("_dns", "_zone", "_record"))
    );

    private final String providerPrefix;

    /**
     * Create a converter for a specific Terraform provider.
     *
     * @param providerPrefix the provider prefix stripped from type names (e.g. "aws", "google")
     */
    public TerraformSchemaConverter(String providerPrefix) {
        this.providerPrefix = providerPrefix;
    }

    // ------------------------------------------------------------------
    // Type name conversion
    // ------------------------------------------------------------------

    /**
     * Convert a Terraform type name to a Kite PascalCase type name.
     *
     * <p>Strips the provider prefix and converts each remaining underscore-delimited
     * segment to title case. Numbers and acronyms at segment starts are preserved
     * naturally (e.g. {@code s3} stays as {@code S3}).</p>
     *
     * <p>Examples with prefix "aws":
     * <ul>
     *   <li>{@code aws_instance} -> {@code Instance}</li>
     *   <li>{@code aws_s3_bucket} -> {@code S3Bucket}</li>
     *   <li>{@code aws_security_group} -> {@code SecurityGroup}</li>
     * </ul>
     *
     * @param tfTypeName the full Terraform type name (e.g. "aws_instance")
     * @return the PascalCase Kite type name
     */
    public String toKiteTypeName(String tfTypeName) {
        // Strip the provider prefix and leading underscore
        var withoutPrefix = tfTypeName.substring(providerPrefix.length() + 1);
        var parts = withoutPrefix.split("_");
        var sb = new StringBuilder(withoutPrefix.length());

        for (var part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1));
        }

        return sb.toString();
    }

    /**
     * Convert a Terraform data source type name to its Kite PascalCase type name.
     *
     * <p>Terraform keeps resources and data sources in separate namespaces (a
     * provider commonly exposes both under one TF type name, addressed via the
     * {@code data.} prefix). Kite's registry and engine lookup are keyed by a
     * single flat type name, so data sources get a deterministic {@code Data}
     * suffix to coexist with the same-named resource
     * (kitecorp/kite-providers#13).</p>
     *
     * <p>Examples with prefix "aws":
     * <ul>
     *   <li>data source {@code aws_ami} -> {@code AmiData}</li>
     *   <li>data source {@code aws_instance} -> {@code InstanceData}</li>
     * </ul>
     *
     * @param tfTypeName the full Terraform data source type name (e.g. "aws_ami")
     * @return the PascalCase Kite type name with the {@code Data} suffix
     */
    public String toKiteDataSourceTypeName(String tfTypeName) {
        return toKiteTypeName(tfTypeName) + "Data";
    }

    // ------------------------------------------------------------------
    // Domain grouping
    // ------------------------------------------------------------------

    /**
     * Determine the Kite import domain for a Terraform type name using heuristic pattern matching.
     *
     * <p>The suffix of the type name (after stripping the provider prefix) is matched
     * against known domain patterns. DNS-specific patterns ({@code route53_zone},
     * {@code route53_record}) are checked before the general networking patterns
     * to avoid misclassification.</p>
     *
     * @param tfTypeName the full Terraform type name (e.g. "aws_s3_bucket")
     * @return the domain string (e.g. "storage", "compute", "networking")
     */
    public String getDomain(String tfTypeName) {
        // Strip provider prefix for matching
        var suffix = tfTypeName.substring(providerPrefix.length());

        // DNS-specific check: route53_zone/route53_record must be classified as dns,
        // not networking (which also matches _route_table patterns)
        if (suffix.contains("_route53_zone") || suffix.contains("_route53_record")) {
            return "dns";
        }

        for (var rule : DOMAIN_RULES) {
            for (var pattern : rule.getValue()) {
                if (suffix.contains(pattern)) {
                    return rule.getKey();
                }
            }
        }

        return "resources";
    }

    // ------------------------------------------------------------------
    // cty type parsing
    // ------------------------------------------------------------------

    /**
     * Parse a JSON-encoded cty type constraint into a Kite type string.
     *
     * <p>Mapping:
     * <ul>
     *   <li>{@code "string"} -> {@code string}</li>
     *   <li>{@code "number"} -> {@code number}</li>
     *   <li>{@code "bool"} -> {@code boolean}</li>
     *   <li>{@code "dynamic"} -> {@code any}</li>
     *   <li>{@code ["list", T]} or {@code ["set", T]} -> {@code T[]}</li>
     *   <li>{@code ["map", T]} -> {@code Map}</li>
     *   <li>{@code ["object", {...}]} -> {@code Map}</li>
     * </ul>
     *
     * @param typeJson JSON-encoded cty type constraint
     * @return the Kite type string
     */
    public String ctyTypeToKiteType(String typeJson) {
        var typeNode = parseTypeJson(typeJson);
        return resolveKiteType(typeNode);
    }

    /**
     * Convenience overload for raw cty type constraint bytes as carried in the
     * protobuf {@code Schema.Attribute.type} field.
     *
     * @param typeBytes UTF-8 JSON-encoded cty type constraint
     * @return the Kite type string
     */
    public String ctyTypeToKiteType(byte[] typeBytes) {
        return ctyTypeToKiteType(new String(typeBytes, StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------
    // Full schema conversion
    // ------------------------------------------------------------------

    /**
     * Convert a Terraform Schema to a complete Kite schema DSL string.
     *
     * <p>Produces output in the form:
     * <pre>
     * schema TypeName {
     *     string propertyName
     *     {@literal @}cloud string computedProperty
     *     {@literal @}sensitive string secretProperty
     *     NestedType nestedProperty
     * }
     * </pre>
     *
     * @param tfTypeName the full Terraform type name (e.g. "aws_instance")
     * @param schema the protocol-neutral schema from a GetProviderSchema response
     * @return the Kite schema DSL string
     */
    public String toKiteSchema(String tfTypeName, TfSchema schema) {
        return toKiteSchemaNamed(toKiteTypeName(tfTypeName), schema);
    }

    /**
     * Convert a Terraform Schema to a Kite schema DSL string under an explicit
     * Kite type name — used for data sources, whose registered name carries the
     * {@code Data} suffix rather than the plain conversion of the TF type name.
     *
     * @param kiteTypeName the Kite type name to declare (e.g. "AmiData")
     * @param schema the protocol-neutral schema from a GetProviderSchema response
     * @return the Kite schema DSL string
     */
    public String toKiteSchemaNamed(String kiteTypeName, TfSchema schema) {
        var sb = new StringBuilder();
        sb.append("schema ").append(kiteTypeName).append(" {\n");

        appendBlockProperties(sb, schema.block(), "    ");

        sb.append("}");
        return sb.toString();
    }

    /**
     * Convenience overload for callers holding a raw tfplugin5 schema message
     * (e.g. protocol-5 raw-RPC tests).
     *
     * @param tfTypeName the full Terraform type name (e.g. "aws_instance")
     * @param schema the tfplugin5 schema from a GetProviderSchema response
     * @return the Kite schema DSL string
     */
    public String toKiteSchema(String tfTypeName, tfplugin5.Tfplugin5.Schema schema) {
        return toKiteSchema(tfTypeName, Tfplugin5Rpc.toTfSchema(schema));
    }

    /**
     * Convert a Terraform schema into the structured {@code kite-api} schema the
     * SDK serves over {@code GetProviderSchema}. This — not the DSL string — is
     * how per-property flags (notably {@code sensitive}, which the engine needs
     * for plan masking, kitecorp/kite-providers#6) reach the engine process.
     *
     * <p>Nested blocks map to a single property each; a sensitive attribute
     * anywhere inside one marks that property sensitive, mirroring
     * {@link #appendNestedBlock}. GROUP blocks flatten into the parent.</p>
     *
     * @param kiteTypeName the Kite type name to declare (e.g. "Password")
     * @param schema the protocol-neutral schema from a GetProviderSchema response
     * @return the structured api schema with per-property flags
     */
    public cloud.kitelang.api.schema.Schema toApiSchema(String kiteTypeName, TfSchema schema) {
        var properties = new LinkedHashSet<Property>();
        collectApiProperties(properties, schema.block());
        return cloud.kitelang.api.schema.Schema.builder()
                .name(kiteTypeName)
                .properties(properties)
                .build();
    }

    /**
     * Convenience overload for callers holding a raw tfplugin5 schema message,
     * mirroring {@link #toKiteSchema(String, tfplugin5.Tfplugin5.Schema)}.
     *
     * @param kiteTypeName the Kite type name to declare (e.g. "Password")
     * @param schema the tfplugin5 schema from a GetProviderSchema response
     * @return the structured api schema with per-property flags
     */
    public cloud.kitelang.api.schema.Schema toApiSchema(String kiteTypeName, tfplugin5.Tfplugin5.Schema schema) {
        return toApiSchema(kiteTypeName, Tfplugin5Rpc.toTfSchema(schema));
    }

    /** Collect api properties for a block: attributes first, then nested blocks. */
    private void collectApiProperties(Set<Property> properties, TfBlock block) {
        for (var attr : block.attributes()) {
            properties.add(Property.builder()
                    .name(TerraformPropertyMapper.toCamelCase(attr.name()))
                    .type(ctyTypeToKiteType(attr.typeJson()))
                    .cloud(attr.computed())
                    .sensitive(attr.sensitive())
                    .build());
        }
        for (var nestedBlock : block.blockTypes()) {
            if (nestedBlock.nesting() == TfNestedBlock.Nesting.GROUP) {
                collectApiProperties(properties, nestedBlock.block());
                continue;
            }
            var nestedTypeName = toPascalCase(nestedBlock.typeName());
            var kiteType = switch (nestedBlock.nesting()) {
                case SINGLE -> nestedTypeName;
                case LIST, SET -> nestedTypeName + "[]";
                case MAP -> "Map";
                default -> "any";
            };
            properties.add(Property.builder()
                    .name(TerraformPropertyMapper.toCamelCase(nestedBlock.typeName()))
                    .type(kiteType)
                    .sensitive(nestedBlock.block().hasSensitiveValues())
                    .build());
        }
    }

    // ------------------------------------------------------------------
    // Internal: block and attribute rendering
    // ------------------------------------------------------------------

    /**
     * Append all attributes and nested blocks from a Block to the string builder.
     *
     * <p>Attributes are rendered as typed properties with decorators.
     * Nested blocks are rendered according to their nesting mode.</p>
     */
    private void appendBlockProperties(StringBuilder sb, TfBlock block, String indent) {
        // Render attributes
        for (var attr : block.attributes()) {
            appendAttribute(sb, attr, indent);
        }

        // Render nested blocks
        for (var nestedBlock : block.blockTypes()) {
            appendNestedBlock(sb, nestedBlock, indent);
        }
    }

    /**
     * Append a single attribute as a Kite property line.
     *
     * <p>Applies decorator rules based on computed/sensitive/writeOnly flags
     * and converts the attribute name from snake_case to camelCase.</p>
     */
    private void appendAttribute(StringBuilder sb, TfAttribute attr, String indent) {
        var kiteType = ctyTypeToKiteType(attr.typeJson());
        var kiteName = TerraformPropertyMapper.toCamelCase(attr.name());

        sb.append(indent);

        // Write-only comment (future: @writeOnly decorator)
        if (attr.writeOnly()) {
            sb.append("// writeOnly\n").append(indent);
        }

        // @sensitive decorator
        if (attr.sensitive()) {
            sb.append("@sensitive ");
        }

        // @cloud decorator for computed properties
        if (attr.computed()) {
            sb.append("@cloud ");
        }

        sb.append(kiteType).append(' ').append(kiteName).append('\n');
    }

    /**
     * Append a nested block according to its nesting mode.
     *
     * <ul>
     *   <li>{@code SINGLE} -> {@code NestedType propertyName}</li>
     *   <li>{@code LIST} or {@code SET} -> {@code NestedType[] propertyName}</li>
     *   <li>{@code MAP} -> {@code Map propertyName}</li>
     *   <li>{@code GROUP} -> flatten attributes into parent block</li>
     * </ul>
     */
    private void appendNestedBlock(StringBuilder sb, TfNestedBlock nestedBlock, String indent) {
        var blockName = nestedBlock.typeName();
        var kiteName = TerraformPropertyMapper.toCamelCase(blockName);
        var nestedTypeName = toPascalCase(blockName);

        // A nested block renders as one property line, so a sensitive attribute
        // anywhere inside it can only surface here — GROUP flattens attributes
        // into the parent, where each keeps its own @sensitive
        var sensitivePrefix = nestedBlock.nesting() != TfNestedBlock.Nesting.GROUP
                              && nestedBlock.block().hasSensitiveValues()
                ? "@sensitive "
                : "";

        switch (nestedBlock.nesting()) {
            // Flatten: merge nested block's attributes into the parent
            case GROUP -> appendBlockProperties(sb, nestedBlock.block(), indent);
            case SINGLE -> sb.append(indent).append(sensitivePrefix).append(nestedTypeName).append(' ')
                    .append(kiteName).append('\n');
            case LIST, SET -> sb.append(indent).append(sensitivePrefix).append(nestedTypeName).append("[] ")
                    .append(kiteName).append('\n');
            case MAP -> sb.append(indent).append(sensitivePrefix).append("Map ").append(kiteName).append('\n');
            default -> sb.append(indent).append("// unsupported nesting mode: ")
                    .append(nestedBlock.nesting()).append('\n');
        }
    }

    // ------------------------------------------------------------------
    // Internal: type resolution helpers
    // ------------------------------------------------------------------

    /**
     * Parse a JSON string into a JsonNode tree.
     */
    private JsonNode parseTypeJson(String typeJson) {
        try {
            return JSON.readTree(typeJson);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse cty type JSON", e);
        }
    }

    /**
     * Resolve a parsed cty type node to a Kite type string.
     *
     * <p>Dispatches between primitive types (text nodes) and compound types (array nodes).</p>
     */
    private String resolveKiteType(JsonNode typeNode) {
        if (typeNode.isTextual()) {
            return resolvePrimitiveType(typeNode.asText());
        }
        if (typeNode.isArray() && typeNode.size() >= 2) {
            return resolveCompoundType(typeNode);
        }
        return "any";
    }

    /**
     * Map a cty primitive type name to a Kite type string.
     */
    private String resolvePrimitiveType(String ctyType) {
        return switch (ctyType) {
            case "string" -> "string";
            case "number" -> "number";
            case "bool" -> "boolean";
            case "dynamic" -> "any";
            default -> "any";
        };
    }

    /**
     * Map a cty compound type (array node) to a Kite type string.
     *
     * <p>Handles list, set (both produce {@code T[]}), map, and object types.</p>
     */
    private String resolveCompoundType(JsonNode typeNode) {
        var kind = typeNode.get(0).asText();
        return switch (kind) {
            case "list", "set" -> resolveKiteType(typeNode.get(1)) + "[]";
            case "map", "object" -> "Map";
            default -> "any";
        };
    }

    /**
     * Convert a snake_case name to PascalCase (for nested block type names).
     *
     * <p>Each underscore-delimited segment is title-cased and concatenated.</p>
     */
    private static String toPascalCase(String snakeCase) {
        var parts = snakeCase.split("_");
        var sb = new StringBuilder(snakeCase.length());
        for (var part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1));
        }
        return sb.toString();
    }
}
