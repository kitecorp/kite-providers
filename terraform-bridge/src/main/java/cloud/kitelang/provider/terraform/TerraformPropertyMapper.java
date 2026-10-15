package cloud.kitelang.provider.terraform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

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
     * <p>Runs of consecutive uppercase letters are treated as a single acronym,
     * not one word per letter, so acronym-heavy AWS/GCP names convert correctly:
     * {@code vpcID} and {@code VPCId} both become {@code vpc_id}, {@code myARN}
     * becomes {@code my_arn}, {@code IAMRole} becomes {@code iam_role}. The final
     * letter of a run only starts a new word when a lowercase letter follows it.</p>
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
                if (startsNewWord(camelCase, i)) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(ch));
            } else {
                sb.append(ch);
            }
        }

        return sb.toString();
    }

    /**
     * Decides whether the uppercase letter at {@code index} begins a new word,
     * so that exactly one underscore is inserted per word boundary.
     *
     * <p>Two boundary shapes exist:
     * <ul>
     *   <li>a camelCase hump — the previous character is lowercase or a digit
     *       (the {@code T} in {@code instanceType}, the {@code B} in {@code s3Bucket});</li>
     *   <li>the end of an acronym run — the previous character is uppercase but a
     *       lowercase letter follows this one, so this letter starts the next word
     *       (the {@code I} in {@code VPCId} → {@code vpc_id}).</li>
     * </ul>
     * Interior letters of an acronym run (upper preceded and followed by upper, or
     * ending the string) are not boundaries, so {@code myARN} stays {@code my_arn}.
     * The first character is never a boundary.</p>
     */
    private static boolean startsNewWord(String name, int index) {
        if (index == 0) {
            return false;
        }
        var previous = name.charAt(index - 1);
        if (!Character.isUpperCase(previous)) {
            return true; // lowercase/digit -> uppercase: camelCase hump
        }
        // Previous letter is uppercase: only a boundary when a new lowercase word starts here.
        var hasLowercaseSuccessor = index + 1 < name.length()
                && Character.isLowerCase(name.charAt(index + 1));
        return hasLowercaseSuccessor;
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
    // Conversions along a cty type
    // ------------------------------------------------------------------

    /**
     * Converts a camelCase property map to snake_case along its cty object type: the keys
     * of objects (attributes and nested blocks) are converted, the keys of maps are not.
     * A map's keys are the user's data: {@code tags = { Name: "web" }} must reach the
     * provider as the tag {@code Name} (kitecorp/kite#109). A value whose attribute the
     * type does not name is converted deeply, as {@link #toSnakeCase(Map)} does.
     *
     * @param properties    the property map with camelCase keys, may be {@code null}
     * @param ctyObjectType the JSON-encoded cty object type of the map
     * @return a new map with snake_case attribute names
     */
    public static Map<String, Object> toSnakeCase(Map<String, Object> properties, String ctyObjectType) {
        return convertObject(properties, parseType(ctyObjectType), TerraformPropertyMapper::toSnakeCase, true);
    }

    /**
     * Converts a snake_case property map to camelCase along its cty object type, keeping
     * the keys of maps as they are; see {@link #toSnakeCase(Map, String)}.
     *
     * @param properties    the property map with snake_case keys, may be {@code null}
     * @param ctyObjectType the JSON-encoded cty object type of the map
     * @return a new map with camelCase attribute names
     */
    public static Map<String, Object> toCamelCase(Map<String, Object> properties, String ctyObjectType) {
        return convertObject(properties, parseType(ctyObjectType), TerraformPropertyMapper::toCamelCase, false);
    }

    /**
     * Converts an object's keys, then each value along its attribute's type. The type is
     * keyed by snake_case names, so it is looked up by the converted key when converting
     * to snake_case and by the original key when converting to camelCase.
     */
    private static Map<String, Object> convertObject(Map<String, Object> object, JsonNode objectType,
                                                     UnaryOperator<String> keyMapper, boolean toSnake) {
        if (object == null || object.isEmpty()) {
            return Collections.emptyMap();
        }
        var attributeTypes = isKind(objectType, "object") ? objectType.get(1) : null;
        var result = new LinkedHashMap<String, Object>(object.size());
        for (var entry : object.entrySet()) {
            var key = keyMapper.apply(entry.getKey());
            var attributeType = attributeTypes == null ? null : attributeTypes.get(toSnake ? key : entry.getKey());
            result.put(key, attributeType == null
                    ? convertValue(entry.getValue(), keyMapper)
                    : convertAlong(entry.getValue(), attributeType, keyMapper, toSnake));
        }
        return result;
    }

    /** Converts a value along its cty type: objects convert their keys, maps keep theirs. */
    @SuppressWarnings("unchecked")
    private static Object convertAlong(Object value, JsonNode type, UnaryOperator<String> keyMapper, boolean toSnake) {
        if (value instanceof Map<?, ?> map && isKind(type, "object")) {
            return convertObject((Map<String, Object>) map, type, keyMapper, toSnake);
        }
        if (value instanceof Map<?, ?> map && isKind(type, "map")) {
            var result = new LinkedHashMap<String, Object>(map.size());
            map.forEach((key, element) -> result.put((String) key, convertAlong(element, type.get(1), keyMapper, toSnake)));
            return result;
        }
        if (value instanceof List<?> list && (isKind(type, "list") || isKind(type, "set"))) {
            return list.stream().map(element -> convertAlong(element, type.get(1), keyMapper, toSnake)).toList();
        }
        // Primitives, and dynamic values, whose keys are the user's
        return value;
    }

    /** Whether a cty type is the compound {@code [kind, ...]}. */
    private static boolean isKind(JsonNode type, String kind) {
        return type != null && type.isArray() && type.size() > 1 && kind.equals(type.get(0).asText());
    }

    private static JsonNode parseType(String ctyType) {
        try {
            return JSON.readTree(ctyType);
        } catch (IOException e) {
            throw new UncheckedIOException("Invalid cty type JSON: " + ctyType, e);
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Generic recursive key-converter. Walks the map tree and applies
     * {@code keyMapper} to every {@link String} key.
     */
    private static Map<String, Object> convertMapKeys(
            Map<String, Object> map,
            UnaryOperator<String> keyMapper
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
            UnaryOperator<String> keyMapper
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
