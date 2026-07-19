package cloud.kitelang.provider.terraform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TerraformPropertyMapper} covering bidirectional
 * snake_case/camelCase conversion of property names and deep map transformation.
 */
class TerraformPropertyMapperTest {

    // ---------------------------------------------------------------
    // toCamelCase(String)
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("toCamelCase(String)")
    class ToCamelCaseString {

        @Test
        @DisplayName("should preserve single-word names unchanged")
        void shouldPreserveSingleWord() {
            assertEquals("ami", TerraformPropertyMapper.toCamelCase("ami"));
            assertEquals("name", TerraformPropertyMapper.toCamelCase("name"));
            assertEquals("id", TerraformPropertyMapper.toCamelCase("id"));
        }

        @Test
        @DisplayName("should convert multi-word snake_case to camelCase")
        void shouldConvertMultiWord() {
            assertEquals("instanceType", TerraformPropertyMapper.toCamelCase("instance_type"));
            assertEquals("vpcSecurityGroupIds", TerraformPropertyMapper.toCamelCase("vpc_security_group_ids"));
            assertEquals("availabilityZone", TerraformPropertyMapper.toCamelCase("availability_zone"));
        }

        @Test
        @DisplayName("should handle acronyms naturally")
        void shouldHandleAcronyms() {
            assertEquals("s3Bucket", TerraformPropertyMapper.toCamelCase("s3_bucket"));
            assertEquals("iamRole", TerraformPropertyMapper.toCamelCase("iam_role"));
            assertEquals("ec2Instance", TerraformPropertyMapper.toCamelCase("ec2_instance"));
        }

        @Test
        @DisplayName("should handle numbers embedded in segments")
        void shouldHandleNumbers() {
            assertEquals("ipv6CidrBlock", TerraformPropertyMapper.toCamelCase("ipv6_cidr_block"));
            assertEquals("route53Zone", TerraformPropertyMapper.toCamelCase("route53_zone"));
        }

        @Test
        @DisplayName("should be idempotent for already-camelCase names")
        void shouldBeIdempotent() {
            assertEquals("instanceType", TerraformPropertyMapper.toCamelCase("instanceType"));
            assertEquals("vpcSecurityGroupIds", TerraformPropertyMapper.toCamelCase("vpcSecurityGroupIds"));
        }

        @Test
        @DisplayName("should return empty string for empty input")
        void shouldReturnEmptyForEmpty() {
            assertEquals("", TerraformPropertyMapper.toCamelCase(""));
        }

        @Test
        @DisplayName("should return empty string for null input")
        void shouldReturnEmptyForNull() {
            assertEquals("", TerraformPropertyMapper.toCamelCase((String) null));
        }

        @Test
        @DisplayName("should handle leading and trailing underscores")
        void shouldHandleLeadingTrailingUnderscores() {
            assertEquals("fooBar", TerraformPropertyMapper.toCamelCase("_foo_bar"));
            assertEquals("fooBar", TerraformPropertyMapper.toCamelCase("foo_bar_"));
        }

        @Test
        @DisplayName("should handle consecutive underscores")
        void shouldHandleConsecutiveUnderscores() {
            assertEquals("fooBar", TerraformPropertyMapper.toCamelCase("foo__bar"));
        }
    }

    // ---------------------------------------------------------------
    // toSnakeCase(String)
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("toSnakeCase(String)")
    class ToSnakeCaseString {

        @Test
        @DisplayName("should preserve single-word lowercase names unchanged")
        void shouldPreserveSingleWord() {
            assertEquals("ami", TerraformPropertyMapper.toSnakeCase("ami"));
            assertEquals("name", TerraformPropertyMapper.toSnakeCase("name"));
            assertEquals("id", TerraformPropertyMapper.toSnakeCase("id"));
        }

        @Test
        @DisplayName("should convert camelCase to snake_case")
        void shouldConvertCamelCase() {
            assertEquals("instance_type", TerraformPropertyMapper.toSnakeCase("instanceType"));
            assertEquals("vpc_security_group_ids", TerraformPropertyMapper.toSnakeCase("vpcSecurityGroupIds"));
            assertEquals("availability_zone", TerraformPropertyMapper.toSnakeCase("availabilityZone"));
        }

        @Test
        @DisplayName("should handle acronyms/numbers naturally")
        void shouldHandleAcronyms() {
            assertEquals("s3_bucket", TerraformPropertyMapper.toSnakeCase("s3Bucket"));
            assertEquals("iam_role", TerraformPropertyMapper.toSnakeCase("iamRole"));
            assertEquals("ipv6_cidr_block", TerraformPropertyMapper.toSnakeCase("ipv6CidrBlock"));
        }

        @Test
        @DisplayName("should treat a trailing uppercase acronym as one word (kitecorp/kite#30)")
        void shouldCollapseTrailingAcronym() {
            // A run of uppercase letters is one acronym, not one word per letter:
            // "vpcID" is vpc + id, so "vpc_id" — never "vpc_i_d".
            assertEquals("vpc_id", TerraformPropertyMapper.toSnakeCase("vpcID"));
            assertEquals("my_arn", TerraformPropertyMapper.toSnakeCase("myARN"));
        }

        @Test
        @DisplayName("should split a leading acronym before the next word (kitecorp/kite#30)")
        void shouldSplitLeadingAcronym() {
            // The last uppercase letter of a run starts the next word when a
            // lowercase letter follows: "VPCId" is vpc + id, "IAMRole" is iam + role.
            assertEquals("vpc_id", TerraformPropertyMapper.toSnakeCase("VPCId"));
            assertEquals("iam_role", TerraformPropertyMapper.toSnakeCase("IAMRole"));
        }

        @Test
        @DisplayName("should treat a leading PascalCase acronym-word as one word (kitecorp/kite#30)")
        void shouldHandlePascalCaseAcronymWord() {
            // "S3Bucket" (PascalCase, digit inside the acronym) is s3 + bucket.
            assertEquals("s3_bucket", TerraformPropertyMapper.toSnakeCase("S3Bucket"));
        }

        @Test
        @DisplayName("should be idempotent for already snake_case names")
        void shouldBeIdempotent() {
            assertEquals("instance_type", TerraformPropertyMapper.toSnakeCase("instance_type"));
            assertEquals("vpc_security_group_ids", TerraformPropertyMapper.toSnakeCase("vpc_security_group_ids"));
        }

        @Test
        @DisplayName("should return empty string for empty input")
        void shouldReturnEmptyForEmpty() {
            assertEquals("", TerraformPropertyMapper.toSnakeCase(""));
        }

        @Test
        @DisplayName("should return empty string for null input")
        void shouldReturnEmptyForNull() {
            assertEquals("", TerraformPropertyMapper.toSnakeCase((String) null));
        }
    }

    // ---------------------------------------------------------------
    // toCamelCase(Map)
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("toCamelCase(Map)")
    class ToCamelCaseMap {

        @Test
        @DisplayName("should convert flat map keys to camelCase")
        void shouldConvertFlatMapKeys() {
            var input = Map.<String, Object>of(
                    "instance_type", "t3.micro",
                    "availability_zone", "us-east-1a"
            );

            var result = TerraformPropertyMapper.toCamelCase(input);

            assertEquals("t3.micro", result.get("instanceType"));
            assertEquals("us-east-1a", result.get("availabilityZone"));
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("should recursively convert nested map keys")
        void shouldConvertNestedMapKeys() {
            var nested = Map.of("from_port", 80, "to_port", 443);
            var input = Map.of("security_group", (Object) nested);

            var result = TerraformPropertyMapper.toCamelCase(input);

            @SuppressWarnings("unchecked")
            var innerMap = (Map<String, Object>) result.get("securityGroup");
            assertNotNull(innerMap);
            assertEquals(80, innerMap.get("fromPort"));
            assertEquals(443, innerMap.get("toPort"));
        }

        @Test
        @DisplayName("should convert keys inside list elements")
        void shouldConvertListOfMaps() {
            var rule1 = Map.of("from_port", (Object) 80, "to_port", (Object) 443);
            var rule2 = Map.of("from_port", (Object) 22, "to_port", (Object) 22);
            var input = Map.of("ingress_rules", (Object) List.of(rule1, rule2));

            var result = TerraformPropertyMapper.toCamelCase(input);

            @SuppressWarnings("unchecked")
            var rules = (List<Map<String, Object>>) result.get("ingressRules");
            assertNotNull(rules);
            assertEquals(2, rules.size());
            assertEquals(80, rules.get(0).get("fromPort"));
            assertEquals(22, rules.get(1).get("fromPort"));
        }

        @Test
        @DisplayName("should preserve null values without crashing")
        void shouldPreserveNullValues() {
            var input = new HashMap<String, Object>();
            input.put("instance_type", null);
            input.put("ami_id", "ami-123");

            var result = TerraformPropertyMapper.toCamelCase(input);

            assertTrue(result.containsKey("instanceType"));
            assertNull(result.get("instanceType"));
            assertEquals("ami-123", result.get("amiId"));
        }

        @Test
        @DisplayName("should return empty map for empty input")
        void shouldReturnEmptyForEmpty() {
            var result = TerraformPropertyMapper.toCamelCase(Collections.<String, Object>emptyMap());
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return empty map for null input")
        void shouldReturnEmptyForNull() {
            var result = TerraformPropertyMapper.toCamelCase((Map<String, Object>) null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should leave non-map list elements unchanged")
        void shouldLeaveNonMapListElementsUnchanged() {
            var input = Map.of("security_group_ids", (Object) List.of("sg-111", "sg-222"));

            var result = TerraformPropertyMapper.toCamelCase(input);

            @SuppressWarnings("unchecked")
            var ids = (List<String>) result.get("securityGroupIds");
            assertEquals(List.of("sg-111", "sg-222"), ids);
        }
    }

    // ---------------------------------------------------------------
    // toSnakeCase(Map)
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("toSnakeCase(Map)")
    class ToSnakeCaseMap {

        @Test
        @DisplayName("should convert flat map keys to snake_case")
        void shouldConvertFlatMapKeys() {
            var input = Map.<String, Object>of(
                    "instanceType", "t3.micro",
                    "availabilityZone", "us-east-1a"
            );

            var result = TerraformPropertyMapper.toSnakeCase(input);

            assertEquals("t3.micro", result.get("instance_type"));
            assertEquals("us-east-1a", result.get("availability_zone"));
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("should recursively convert nested map keys")
        void shouldConvertNestedMapKeys() {
            var nested = Map.of("fromPort", 80, "toPort", 443);
            var input = Map.of("securityGroup", (Object) nested);

            var result = TerraformPropertyMapper.toSnakeCase(input);

            @SuppressWarnings("unchecked")
            var innerMap = (Map<String, Object>) result.get("security_group");
            assertNotNull(innerMap);
            assertEquals(80, innerMap.get("from_port"));
            assertEquals(443, innerMap.get("to_port"));
        }

        @Test
        @DisplayName("should convert keys inside list elements")
        void shouldConvertListOfMaps() {
            var rule = Map.of("fromPort", (Object) 80, "toPort", (Object) 443);
            var input = Map.of("ingressRules", (Object) List.of(rule));

            var result = TerraformPropertyMapper.toSnakeCase(input);

            @SuppressWarnings("unchecked")
            var rules = (List<Map<String, Object>>) result.get("ingress_rules");
            assertNotNull(rules);
            assertEquals(80, rules.get(0).get("from_port"));
        }

        @Test
        @DisplayName("should preserve null values without crashing")
        void shouldPreserveNullValues() {
            var input = new HashMap<String, Object>();
            input.put("instanceType", null);
            input.put("amiId", "ami-123");

            var result = TerraformPropertyMapper.toSnakeCase(input);

            assertTrue(result.containsKey("instance_type"));
            assertNull(result.get("instance_type"));
            assertEquals("ami-123", result.get("ami_id"));
        }

        @Test
        @DisplayName("should return empty map for null input")
        void shouldReturnEmptyForNull() {
            var result = TerraformPropertyMapper.toSnakeCase((Map<String, Object>) null);
            assertTrue(result.isEmpty());
        }
    }

    // ---------------------------------------------------------------
    // Roundtrip / idempotency
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("Roundtrip conversion")
    class Roundtrip {

        @Test
        @DisplayName("snake -> camel -> snake should produce the original")
        void shouldRoundtripSnakeToCamelToSnake() {
            var original = "vpc_security_group_ids";
            var camel = TerraformPropertyMapper.toCamelCase(original);
            var backToSnake = TerraformPropertyMapper.toSnakeCase(camel);
            assertEquals(original, backToSnake);
        }

        @Test
        @DisplayName("camel -> snake -> camel should produce the original")
        void shouldRoundtripCamelToSnakeToCamel() {
            var original = "vpcSecurityGroupIds";
            var snake = TerraformPropertyMapper.toSnakeCase(original);
            var backToCamel = TerraformPropertyMapper.toCamelCase(snake);
            assertEquals(original, backToCamel);
        }

        @Test
        @DisplayName("real acronym-bearing TF names survive snake -> camel -> snake (kitecorp/kite#30)")
        void shouldRoundtripAcronymTfNames() {
            // TF attribute names are already snake_case with lowercased acronyms;
            // the fixed acronym handling must not disturb their wire round-trip.
            for (var tfName : List.of("vpc_id", "iam_role", "kms_key_id", "ipv6_cidr_block")) {
                var camel = TerraformPropertyMapper.toCamelCase(tfName);
                assertEquals(tfName, TerraformPropertyMapper.toSnakeCase(camel),
                        "round-trip failed for " + tfName + " (camel=" + camel + ")");
            }
        }

        @Test
        @DisplayName("double toCamelCase should be idempotent")
        void shouldBeIdempotentToCamelCase() {
            var once = TerraformPropertyMapper.toCamelCase("instance_type");
            var twice = TerraformPropertyMapper.toCamelCase(once);
            assertEquals(once, twice);
        }

        @Test
        @DisplayName("double toSnakeCase should be idempotent")
        void shouldBeIdempotentToSnakeCase() {
            var once = TerraformPropertyMapper.toSnakeCase("instanceType");
            var twice = TerraformPropertyMapper.toSnakeCase(once);
            assertEquals(once, twice);
        }

        @Test
        @DisplayName("deep map roundtrip snake -> camel -> snake")
        void shouldRoundtripDeepMap() {
            var rule = new HashMap<String, Object>();
            rule.put("from_port", 80);
            rule.put("to_port", 443);
            rule.put("cidr_blocks", List.of("0.0.0.0/0"));

            var input = new HashMap<String, Object>();
            input.put("ingress_rules", List.of(rule));
            input.put("vpc_id", "vpc-123");

            var camelMap = TerraformPropertyMapper.toCamelCase(input);
            var snakeMap = TerraformPropertyMapper.toSnakeCase(camelMap);

            assertEquals(input, snakeMap);
        }
    }
}
