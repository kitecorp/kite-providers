package cloud.kitelang.provider.terraform;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bidirectional property-name converter between Terraform's {@code snake_case}
 * and Kite's {@code camelCase} conventions.
 *
 * <p>Used at runtime during every CRUD operation to translate property names
 * when bridging between the Kite engine and native Terraform providers.</p>
 *
 * <p>All conversions are idempotent: applying the same direction twice
 * produces the same result. Deep (recursive) map variants convert every
 * key in nested maps and in maps contained within lists.</p>
 *
 * <p>This class is stateless and thread-safe; all methods are static.</p>
 */
public final class TerraformPropertyMapper {

    private TerraformPropertyMapper() {
        // utility class
    }

    // ------------------------------------------------------------------
    // String conversions
    // ------------------------------------------------------------------

    /**
     * Converts a {@code snake_case} name to {@code camelCase}.
     *
     * <p>Single-word names and names already in camelCase are returned
     * unchanged (idempotent).</p>
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code instance_type}  -> {@code instanceType}</li>
     *   <li>{@code vpc_security_group_ids} -> {@code vpcSecurityGroupIds}</li>
     *   <li>{@code s3_bucket} -> {@code s3Bucket}</li>
     *   <li>{@code ipv6_cidr_block} -> {@code ipv6CidrBlock}</li>
     * </ul>
     *
     * @param snakeCase the snake_case name, may be {@code null}
     * @return the camelCase equivalent, or empty string if input is null/empty
     */
    public static String toCamelCase(String snakeCase) {
        if (snakeCase == null || snakeCase.isEmpty()) {
            return "";
        }

        // If there are no underscores the name is already a single word or camelCase
        if (snakeCase.indexOf('_') == -1) {
            return snakeCase;
        }

        var parts = snakeCase.split("_");
        var sb = new StringBuilder(snakeCase.length());

        for (var part : parts) {
            if (part.isEmpty()) {
                continue; // skip leading/trailing/consecutive underscores
            }
            if (sb.isEmpty()) {
                // First segment stays lowercase
                sb.append(part);
            } else {
                sb.append(Character.toUpperCase(part.charAt(0)));
                sb.append(part.substring(1));
            }
        }

        return sb.toString();
    }

    /**
     * Converts a {@code camelCase} name to {@code snake_case}.
     *
     * <p>Single-word names and names already in snake_case are returned
     * unchanged (idempotent).</p>
     *
     * <p>A word boundary is detected when a lowercase letter or digit is
     * followed by an uppercase letter. This handles embedded numbers
     * naturally: {@code ipv6CidrBlock} becomes {@code ipv6_cidr_block}.</p>
     *
     * @param camelCase the camelCase name, may be {@code null}
     * @return the snake_case equivalent, or empty string if input is null/empty
     */
    public static String toSnakeCase(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return "";
        }

        var sb = new StringBuilder(camelCase.length() + 4);

        for (var i = 0; i < camelCase.length(); i++) {
            var ch = camelCase.charAt(i);

            if (Character.isUpperCase(ch)) {
                // Insert underscore before uppercase if preceded by lowercase/digit
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(ch));
            } else {
                sb.append(ch);
            }
        }

        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Deep map conversions
    // ------------------------------------------------------------------

    /**
     * Recursively converts all keys in the map from {@code snake_case}
     * to {@code camelCase}. Nested maps and maps inside lists are also converted.
     *
     * @param properties the property map with snake_case keys, may be {@code null}
     * @return a new map with camelCase keys and the same values
     */
    public static Map<String, Object> toCamelCase(Map<String, Object> properties) {
        return convertMapKeys(properties, TerraformPropertyMapper::toCamelCase);
    }

    /**
     * Recursively converts all keys in the map from {@code camelCase}
     * to {@code snake_case}. Nested maps and maps inside lists are also converted.
     *
     * @param properties the property map with camelCase keys, may be {@code null}
     * @return a new map with snake_case keys and the same values
     */
    public static Map<String, Object> toSnakeCase(Map<String, Object> properties) {
        return convertMapKeys(properties, TerraformPropertyMapper::toSnakeCase);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Generic recursive key-converter. Walks the map tree and applies
     * {@code keyMapper} to every {@link String} key.
     */
    private static Map<String, Object> convertMapKeys(
            Map<String, Object> map,
            java.util.function.UnaryOperator<String> keyMapper
    ) {
        if (map == null || map.isEmpty()) {
            return Collections.emptyMap();
        }

        var result = new LinkedHashMap<String, Object>(map.size());

        for (var entry : map.entrySet()) {
            var convertedKey = keyMapper.apply(entry.getKey());
            var convertedValue = convertValue(entry.getValue(), keyMapper);
            result.put(convertedKey, convertedValue);
        }

        return result;
    }

    /**
     * Recursively converts values: if the value is a {@link Map} its keys
     * are converted; if it is a {@link List} each element is inspected.
     * All other values (primitives, strings, nulls) pass through unchanged.
     */
    @SuppressWarnings("unchecked")
    private static Object convertValue(
            Object value,
            java.util.function.UnaryOperator<String> keyMapper
    ) {
        if (value instanceof Map<?, ?> nested) {
            return convertMapKeys((Map<String, Object>) nested, keyMapper);
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(element -> convertValue(element, keyMapper))
                    .toList();
        }
        return value; // primitives, strings, null
    }
}
