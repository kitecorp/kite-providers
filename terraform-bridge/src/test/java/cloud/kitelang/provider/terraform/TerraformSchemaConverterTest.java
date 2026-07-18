package cloud.kitelang.provider.terraform;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tfplugin5.Tfplugin5.Schema;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for {@link TerraformSchemaConverter}.
 *
 * <p>Covers type name conversion, cty type parsing, property mapping,
 * nested block handling, domain grouping, and full schema conversion.</p>
 */
class TerraformSchemaConverterTest {

    private TerraformSchemaConverter converter;

    @BeforeEach
    void setUp() {
        converter = new TerraformSchemaConverter("aws");
    }

    // ---------------------------------------------------------------
    // 1. Type name conversion
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("toKiteTypeName")
    class TypeNameConversion {

        @Test
        @DisplayName("should strip provider prefix and PascalCase single word")
        void shouldConvertSingleWord() {
            var actual = converter.toKiteTypeName("aws_instance");
            assertEquals("Instance", actual);
        }

        @Test
        @DisplayName("should handle multi-word names with PascalCase")
        void shouldConvertMultiWord() {
            var actual = converter.toKiteTypeName("aws_security_group");
            assertEquals("SecurityGroup", actual);
        }

        @Test
        @DisplayName("should preserve numbers at word start like s3")
        void shouldPreserveNumbersLikeS3() {
            var actual = converter.toKiteTypeName("aws_s3_bucket");
            assertEquals("S3Bucket", actual);
        }

        @Test
        @DisplayName("should handle acronyms like iam")
        void shouldHandleAcronymsLikeIam() {
            var actual = converter.toKiteTypeName("aws_iam_role");
            assertEquals("IamRole", actual);
        }

        @Test
        @DisplayName("should handle longer multi-word names")
        void shouldHandleLongerNames() {
            var actual = converter.toKiteTypeName("aws_launch_template");
            assertEquals("LaunchTemplate", actual);
        }

        @Test
        @DisplayName("should work with google provider prefix")
        void shouldWorkWithGooglePrefix() {
            var googleConverter = new TerraformSchemaConverter("google");
            var actual = googleConverter.toKiteTypeName("google_compute_instance");
            assertEquals("ComputeInstance", actual);
        }

        @Test
        @DisplayName("should handle type name that is just the prefix with one word")
        void shouldHandlePrefixPlusOneWord() {
            var actual = converter.toKiteTypeName("aws_vpc");
            assertEquals("Vpc", actual);
        }

        @Test
        @DisplayName("should handle ec2 prefix in resource name")
        void shouldHandleEc2() {
            var actual = converter.toKiteTypeName("aws_ec2_fleet");
            assertEquals("Ec2Fleet", actual);
        }

        @Test
        @DisplayName("should handle datadog provider prefix")
        void shouldHandleDatadogPrefix() {
            var datadogConverter = new TerraformSchemaConverter("datadog");
            var actual = datadogConverter.toKiteTypeName("datadog_monitor");
            assertEquals("Monitor", actual);
        }
    }

    // ---------------------------------------------------------------
    // 2. cty type parsing
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("ctyTypeToKiteType")
    class CtyTypeConversion {

        @Test
        @DisplayName("should convert string primitive")
        void shouldConvertString() {
            var actual = converter.ctyTypeToKiteType(jsonBytes("\"string\""));
            assertEquals("string", actual);
        }

        @Test
        @DisplayName("should convert number primitive")
        void shouldConvertNumber() {
            var actual = converter.ctyTypeToKiteType(jsonBytes("\"number\""));
            assertEquals("number", actual);
        }

        @Test
        @DisplayName("should convert bool primitive to boolean")
        void shouldConvertBool() {
            var actual = converter.ctyTypeToKiteType(jsonBytes("\"bool\""));
            assertEquals("boolean", actual);
        }

        @Test
        @DisplayName("should convert dynamic to any")
        void shouldConvertDynamic() {
            var actual = converter.ctyTypeToKiteType(jsonBytes("\"dynamic\""));
            assertEquals("any", actual);
        }

        @Test
        @DisplayName("should convert list of strings to string[]")
        void shouldConvertListOfStrings() {
            var actual = converter.ctyTypeToKiteType(jsonBytes("[\"list\", \"string\"]"));
            assertEquals("string[]", actual);
        }

        @Test
        @DisplayName("should convert set of strings to string[]")
        void shouldConvertSetOfStrings() {
            var actual = converter.ctyTypeToKiteType(jsonBytes("[\"set\", \"string\"]"));
            assertEquals("string[]", actual);
        }

        @Test
        @DisplayName("should convert list of numbers to number[]")
        void shouldConvertListOfNumbers() {
            var actual = converter.ctyTypeToKiteType(jsonBytes("[\"list\", \"number\"]"));
            assertEquals("number[]", actual);
        }

        @Test
        @DisplayName("should convert map of strings to Map")
        void shouldConvertMapOfStrings() {
            var actual = converter.ctyTypeToKiteType(jsonBytes("[\"map\", \"string\"]"));
            assertEquals("Map", actual);
        }

        @Test
        @DisplayName("should convert object type to Map")
        void shouldConvertObjectType() {
            var actual = converter.ctyTypeToKiteType(
                    jsonBytes("[\"object\", {\"name\": \"string\", \"port\": \"number\"}]"));
            assertEquals("Map", actual);
        }

        @Test
        @DisplayName("should convert list of booleans to boolean[]")
        void shouldConvertListOfBooleans() {
            var actual = converter.ctyTypeToKiteType(jsonBytes("[\"list\", \"bool\"]"));
            assertEquals("boolean[]", actual);
        }
    }

    // ---------------------------------------------------------------
    // 3. Property conversion
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("Property mapping in toKiteSchema")
    class PropertyMapping {

        @Test
        @DisplayName("should produce required property for required=true, computed=false")
        void shouldProduceRequiredProperty() {
            var schema = buildSchema(
                    attribute("ami", "\"string\"", true, false, false, false, false)
            );

            var actual = converter.toKiteSchema("aws_instance", schema);

            assertTrue(actual.contains("string ami"), "Expected 'string ami' but got: " + actual);
            assertFalse(actual.contains("@cloud"), "Should not have @cloud for required property");
        }

        @Test
        @DisplayName("should produce optional property for optional=true, computed=false")
        void shouldProduceOptionalProperty() {
            var schema = buildSchema(
                    attribute("monitoring", "\"bool\"", false, true, false, false, false)
            );

            var actual = converter.toKiteSchema("aws_instance", schema);

            assertTrue(actual.contains("boolean monitoring"),
                    "Expected 'boolean monitoring' but got: " + actual);
        }

        @Test
        @DisplayName("should produce @cloud property for computed=true, optional=false")
        void shouldProduceCloudPropertyForPureComputed() {
            var schema = buildSchema(
                    attribute("public_ip", "\"string\"", false, false, true, false, false)
            );

            var actual = converter.toKiteSchema("aws_instance", schema);

            assertTrue(actual.contains("@cloud string publicIp"),
                    "Expected '@cloud string publicIp' but got: " + actual);
        }

        @Test
        @DisplayName("should produce @cloud property for computed=true, optional=true")
        void shouldProduceCloudPropertyForComputedOptional() {
            var schema = buildSchema(
                    attribute("instance_id", "\"string\"", false, true, true, false, false)
            );

            var actual = converter.toKiteSchema("aws_instance", schema);

            assertTrue(actual.contains("@cloud string instanceId"),
                    "Expected '@cloud string instanceId' but got: " + actual);
        }

        @Test
        @DisplayName("should add @sensitive for sensitive=true")
        void shouldAddSensitiveDecorator() {
            var schema = buildSchema(
                    attribute("password", "\"string\"", true, false, false, true, false)
            );

            var actual = converter.toKiteSchema("aws_instance", schema);

            assertTrue(actual.contains("@sensitive"),
                    "Expected @sensitive but got: " + actual);
            assertTrue(actual.contains("string password"),
                    "Expected 'string password' but got: " + actual);
        }

        @Test
        @DisplayName("should add comment for write_only=true")
        void shouldAddWriteOnlyComment() {
            var schema = buildSchema(
                    attribute("secret_key", "\"string\"", true, false, false, false, true)
            );

            var actual = converter.toKiteSchema("aws_instance", schema);

            assertTrue(actual.contains("writeOnly"),
                    "Expected writeOnly comment but got: " + actual);
        }

        @Test
        @DisplayName("should convert snake_case attribute name to camelCase")
        void shouldConvertAttributeNameToCamelCase() {
            var schema = buildSchema(
                    attribute("instance_type", "\"string\"", true, false, false, false, false)
            );

            var actual = converter.toKiteSchema("aws_instance", schema);

            assertTrue(actual.contains("instanceType"),
                    "Expected camelCase 'instanceType' but got: " + actual);
            assertFalse(actual.contains("instance_type"),
                    "Should not contain snake_case in output");
        }
    }

    // ---------------------------------------------------------------
    // 4. Nested block handling
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("Nested block handling in toKiteSchema")
    class NestedBlockHandling {

        @Test
        @DisplayName("should produce single nested schema for SINGLE nesting mode")
        void shouldHandleSingleNesting() {
            var innerBlock = Schema.Block.newBuilder()
                    .addAttributes(attribute("volume_size", "\"number\"", true, false, false, false, false))
                    .build();
            var nestedBlock = Schema.NestedBlock.newBuilder()
                    .setTypeName("root_block_device")
                    .setBlock(innerBlock)
                    .setNesting(Schema.NestedBlock.NestingMode.SINGLE)
                    .build();
            var schema = Schema.newBuilder()
                    .setBlock(Schema.Block.newBuilder().addBlockTypes(nestedBlock))
                    .build();

            var actual = converter.toKiteSchema("aws_instance", schema);

            assertTrue(actual.contains("RootBlockDevice rootBlockDevice"),
                    "Expected 'RootBlockDevice rootBlockDevice' but got: " + actual);
        }

        @Test
        @DisplayName("should produce array of nested schema for LIST nesting mode")
        void shouldHandleListNesting() {
            var innerBlock = Schema.Block.newBuilder()
                    .addAttributes(attribute("from_port", "\"number\"", true, false, false, false, false))
                    .build();
            var nestedBlock = Schema.NestedBlock.newBuilder()
                    .setTypeName("ingress")
                    .setBlock(innerBlock)
                    .setNesting(Schema.NestedBlock.NestingMode.LIST)
                    .build();
            var schema = Schema.newBuilder()
                    .setBlock(Schema.Block.newBuilder().addBlockTypes(nestedBlock))
                    .build();

            var actual = converter.toKiteSchema("aws_security_group", schema);

            assertTrue(actual.contains("Ingress[] ingress"),
                    "Expected 'Ingress[] ingress' but got: " + actual);
        }

        @Test
        @DisplayName("should produce array of nested schema for SET nesting mode")
        void shouldHandleSetNesting() {
            var innerBlock = Schema.Block.newBuilder()
                    .addAttributes(attribute("cidr_block", "\"string\"", true, false, false, false, false))
                    .build();
            var nestedBlock = Schema.NestedBlock.newBuilder()
                    .setTypeName("egress")
                    .setBlock(innerBlock)
                    .setNesting(Schema.NestedBlock.NestingMode.SET)
                    .build();
            var schema = Schema.newBuilder()
                    .setBlock(Schema.Block.newBuilder().addBlockTypes(nestedBlock))
                    .build();

            var actual = converter.toKiteSchema("aws_security_group", schema);

            assertTrue(actual.contains("Egress[] egress"),
                    "Expected 'Egress[] egress' but got: " + actual);
        }

        @Test
        @DisplayName("should produce Map for MAP nesting mode")
        void shouldHandleMapNesting() {
            var innerBlock = Schema.Block.newBuilder()
                    .addAttributes(attribute("value", "\"string\"", true, false, false, false, false))
                    .build();
            var nestedBlock = Schema.NestedBlock.newBuilder()
                    .setTypeName("setting")
                    .setBlock(innerBlock)
                    .setNesting(Schema.NestedBlock.NestingMode.MAP)
                    .build();
            var schema = Schema.newBuilder()
                    .setBlock(Schema.Block.newBuilder().addBlockTypes(nestedBlock))
                    .build();

            var actual = converter.toKiteSchema("aws_elastic_beanstalk_environment", schema);

            assertTrue(actual.contains("Map setting"),
                    "Expected 'Map setting' but got: " + actual);
        }

        @Test
        @DisplayName("should flatten attributes into parent for GROUP nesting mode")
        void shouldHandleGroupNesting() {
            var innerBlock = Schema.Block.newBuilder()
                    .addAttributes(attribute("iops", "\"number\"", false, true, false, false, false))
                    .build();
            var nestedBlock = Schema.NestedBlock.newBuilder()
                    .setTypeName("performance")
                    .setBlock(innerBlock)
                    .setNesting(Schema.NestedBlock.NestingMode.GROUP)
                    .build();
            var schema = Schema.newBuilder()
                    .setBlock(Schema.Block.newBuilder().addBlockTypes(nestedBlock))
                    .build();

            var actual = converter.toKiteSchema("aws_instance", schema);

            // GROUP means attributes are merged into parent, not nested
            assertTrue(actual.contains("number iops"),
                    "Expected flattened 'number iops' but got: " + actual);
            assertFalse(actual.contains("Performance"),
                    "Should not contain nested type name for GROUP");
        }
    }

    // ---------------------------------------------------------------
    // 5. Domain grouping
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("getDomain")
    class DomainGrouping {

        @Test
        @DisplayName("should classify instance as compute")
        void shouldClassifyInstance() {
            assertEquals("compute", converter.getDomain("aws_instance"));
        }

        @Test
        @DisplayName("should classify launch_template as compute")
        void shouldClassifyLaunchTemplate() {
            assertEquals("compute", converter.getDomain("aws_launch_template"));
        }

        @Test
        @DisplayName("should classify autoscaling_group as compute")
        void shouldClassifyAutoscaling() {
            assertEquals("compute", converter.getDomain("aws_autoscaling_group"));
        }

        @Test
        @DisplayName("should classify vpc as networking")
        void shouldClassifyVpc() {
            assertEquals("networking", converter.getDomain("aws_vpc"));
        }

        @Test
        @DisplayName("should classify subnet as networking")
        void shouldClassifySubnet() {
            assertEquals("networking", converter.getDomain("aws_subnet"));
        }

        @Test
        @DisplayName("should classify security_group as networking")
        void shouldClassifySecurityGroup() {
            assertEquals("networking", converter.getDomain("aws_security_group"));
        }

        @Test
        @DisplayName("should classify route_table as networking")
        void shouldClassifyRouteTable() {
            assertEquals("networking", converter.getDomain("aws_route_table"));
        }

        @Test
        @DisplayName("should classify network_interface as networking")
        void shouldClassifyNetworkInterface() {
            assertEquals("networking", converter.getDomain("aws_network_interface"));
        }

        @Test
        @DisplayName("should classify s3_bucket as storage")
        void shouldClassifyS3Bucket() {
            assertEquals("storage", converter.getDomain("aws_s3_bucket"));
        }

        @Test
        @DisplayName("should classify iam_role as iam")
        void shouldClassifyIamRole() {
            assertEquals("iam", converter.getDomain("aws_iam_role"));
        }

        @Test
        @DisplayName("should classify iam_policy as iam")
        void shouldClassifyIamPolicy() {
            assertEquals("iam", converter.getDomain("aws_iam_policy"));
        }

        @Test
        @DisplayName("should classify rds_cluster as database")
        void shouldClassifyRdsCluster() {
            assertEquals("database", converter.getDomain("aws_rds_cluster"));
        }

        @Test
        @DisplayName("should classify db_instance as database")
        void shouldClassifyDbInstance() {
            assertEquals("database", converter.getDomain("aws_db_instance"));
        }

        @Test
        @DisplayName("should classify dynamodb_table as database")
        void shouldClassifyDynamodb() {
            assertEquals("database", converter.getDomain("aws_dynamodb_table"));
        }

        @Test
        @DisplayName("should classify lambda_function as serverless")
        void shouldClassifyLambda() {
            assertEquals("serverless", converter.getDomain("aws_lambda_function"));
        }

        @Test
        @DisplayName("should classify route53_zone as dns")
        void shouldClassifyRoute53Zone() {
            assertEquals("dns", converter.getDomain("aws_route53_zone"));
        }

        @Test
        @DisplayName("should classify route53_record as dns")
        void shouldClassifyRoute53Record() {
            assertEquals("dns", converter.getDomain("aws_route53_record"));
        }

        @Test
        @DisplayName("should classify lb as loadbalancing")
        void shouldClassifyLb() {
            assertEquals("loadbalancing", converter.getDomain("aws_lb"));
        }

        @Test
        @DisplayName("should classify lb_target_group as loadbalancing")
        void shouldClassifyLbTargetGroup() {
            assertEquals("loadbalancing", converter.getDomain("aws_lb_target_group"));
        }

        @Test
        @DisplayName("should classify unknown types as resources")
        void shouldClassifyUnknownAsResources() {
            assertEquals("resources", converter.getDomain("aws_cloudwatch_metric_alarm"));
        }

        @Test
        @DisplayName("should classify google storage_bucket as storage")
        void shouldClassifyGoogleStorage() {
            var googleConverter = new TerraformSchemaConverter("google");
            assertEquals("storage", googleConverter.getDomain("google_storage_bucket"));
        }
    }

    // ---------------------------------------------------------------
    // 6. Full schema conversion
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("Full schema conversion")
    class FullSchemaConversion {

        @Test
        @DisplayName("should produce valid Kite schema DSL for a simple resource")
        void shouldProduceValidKiteSchema() {
            var schema = Schema.newBuilder()
                    .setBlock(Schema.Block.newBuilder()
                            .addAttributes(attribute("ami", "\"string\"", true, false, false, false, false))
                            .addAttributes(attribute("instance_type", "\"string\"", true, false, false, false, false))
                            .addAttributes(attribute("monitoring", "\"bool\"", false, true, false, false, false))
                            .addAttributes(attribute("public_ip", "\"string\"", false, false, true, false, false))
                            .addAttributes(attribute("id", "\"string\"", false, false, true, false, false))
                    ).build();

            var actual = converter.toKiteSchema("aws_instance", schema);

            assertTrue(actual.startsWith("schema Instance {"), "Should start with schema declaration");
            assertTrue(actual.contains("string ami"), "Should have required ami");
            assertTrue(actual.contains("string instanceType"), "Should have required instanceType");
            assertTrue(actual.contains("boolean monitoring"), "Should have optional monitoring");
            assertTrue(actual.contains("@cloud string publicIp"), "Should have @cloud publicIp");
            assertTrue(actual.contains("@cloud string id"), "Should have @cloud id");
            assertTrue(actual.endsWith("}"), "Should end with closing brace");
        }

        @Test
        @DisplayName("should produce schema with array type properties")
        void shouldProduceSchemaWithArrayTypes() {
            var schema = buildSchema(
                    attribute("security_group_ids", "[\"list\", \"string\"]", false, true, false, false, false)
            );

            var actual = converter.toKiteSchema("aws_instance", schema);

            assertTrue(actual.contains("string[] securityGroupIds"),
                    "Expected 'string[] securityGroupIds' but got: " + actual);
        }

        @Test
        @DisplayName("should produce schema with both attributes and nested blocks")
        void shouldProduceSchemaWithNestedBlocks() {
            var innerBlock = Schema.Block.newBuilder()
                    .addAttributes(attribute("volume_size", "\"number\"", true, false, false, false, false))
                    .addAttributes(attribute("encrypted", "\"bool\"", false, true, false, false, false))
                    .build();
            var nestedBlock = Schema.NestedBlock.newBuilder()
                    .setTypeName("root_block_device")
                    .setBlock(innerBlock)
                    .setNesting(Schema.NestedBlock.NestingMode.SINGLE)
                    .build();

            var schema = Schema.newBuilder()
                    .setBlock(Schema.Block.newBuilder()
                            .addAttributes(attribute("ami", "\"string\"", true, false, false, false, false))
                            .addBlockTypes(nestedBlock)
                    ).build();

            var actual = converter.toKiteSchema("aws_instance", schema);

            assertTrue(actual.contains("string ami"), "Should have ami attribute");
            assertTrue(actual.contains("RootBlockDevice rootBlockDevice"),
                    "Should have nested block property: " + actual);
        }

        @Test
        @DisplayName("should add @sensitive to a nested block property containing a sensitive attribute")
        void shouldMarkNestedBlockContainingSensitiveAttribute() {
            // A nested block renders as a single property line, so a sensitive
            // attribute anywhere inside it can only surface on that line —
            // otherwise the flag is dropped (kitecorp/kite-providers#6)
            var credentialBlock = Schema.NestedBlock.newBuilder()
                    .setTypeName("master_auth")
                    .setBlock(Schema.Block.newBuilder()
                            .addAttributes(attribute("username", "\"string\"", false, true, false, false, false))
                            .addAttributes(attribute("password", "\"string\"", false, true, false, true, false)))
                    .setNesting(Schema.NestedBlock.NestingMode.SINGLE)
                    .build();
            var ruleBlock = Schema.NestedBlock.newBuilder()
                    .setTypeName("ingress")
                    .setBlock(Schema.Block.newBuilder()
                            .addAttributes(attribute("from_port", "\"number\"", false, true, false, false, false)))
                    .setNesting(Schema.NestedBlock.NestingMode.SET)
                    .build();
            var schema = Schema.newBuilder()
                    .setBlock(Schema.Block.newBuilder()
                            .addBlockTypes(credentialBlock)
                            .addBlockTypes(ruleBlock))
                    .build();

            var actual = converter.toKiteSchema("aws_thing", schema);

            assertTrue(actual.contains("@sensitive MasterAuth masterAuth"),
                    "Sensitive-bearing block must carry @sensitive, got: " + actual);
            assertTrue(actual.contains("    Ingress[] ingress"),
                    "Non-sensitive sibling block must stay undecorated, got: " + actual);
        }

        @Test
        @DisplayName("should produce schema with @sensitive and @cloud decorators together")
        void shouldProduceSchemaWithMultipleDecorators() {
            var schema = buildSchema(
                    attribute("db_password", "\"string\"", false, false, true, true, false)
            );

            var actual = converter.toKiteSchema("aws_db_instance", schema);

            assertTrue(actual.contains("@sensitive"), "Expected @sensitive");
            assertTrue(actual.contains("@cloud"), "Expected @cloud");
            assertTrue(actual.contains("string dbPassword"), "Expected camelCase name");
        }

        @Test
        @DisplayName("should handle schema with no attributes")
        void shouldHandleEmptySchema() {
            var schema = Schema.newBuilder()
                    .setBlock(Schema.Block.newBuilder())
                    .build();

            var actual = converter.toKiteSchema("aws_instance", schema);

            assertTrue(actual.contains("schema Instance {"), "Should have declaration");
            assertTrue(actual.trim().endsWith("}"), "Should have closing brace");
        }
    }

    // ---------------------------------------------------------------
    // toApiSchema — structured schema for the provider-SDK wire
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("toApiSchema")
    class ApiSchemaConversion {

        private cloud.kitelang.api.schema.Schema convert(Schema schema) {
            return converter.toApiSchema("Instance", Tfplugin5Rpc.toTfSchema(schema));
        }

        private cloud.kitelang.api.resource.Property property(cloud.kitelang.api.schema.Schema schema, String name) {
            return schema.getProperties().stream()
                    .filter(p -> name.equals(p.name()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "property '" + name + "' missing from " + schema.getProperties()));
        }

        @Test
        @DisplayName("should carry camelCase names, kite types, and cloud/sensitive flags")
        void shouldCarryNamesTypesAndFlags() {
            var schema = buildSchema(
                    attribute("keepers_count", "\"number\"", false, true, false, false, false),
                    attribute("result", "\"string\"", false, false, true, true, false)
            );

            var apiSchema = convert(schema);

            assertEquals("Instance", apiSchema.getName());
            var keepers = property(apiSchema, "keepersCount");
            assertEquals("number", keepers.type());
            assertFalse(keepers.isCloud());
            assertFalse(keepers.isSensitive());
            var result = property(apiSchema, "result");
            assertEquals("string", result.type());
            assertTrue(result.isCloud(), "computed attribute maps to cloud");
            assertTrue(result.isSensitive(), "sensitive flag must survive into the api schema");
        }

        @Test
        @DisplayName("should map nested blocks to properties, sensitive when the block holds a sensitive attribute")
        void shouldMapNestedBlocksWithSensitivity() {
            var authBlock = Schema.NestedBlock.newBuilder()
                    .setTypeName("master_auth")
                    .setBlock(Schema.Block.newBuilder()
                            .addAttributes(attribute("password", "\"string\"", false, true, false, true, false)))
                    .setNesting(Schema.NestedBlock.NestingMode.SINGLE)
                    .build();
            var ruleBlock = Schema.NestedBlock.newBuilder()
                    .setTypeName("ingress")
                    .setBlock(Schema.Block.newBuilder()
                            .addAttributes(attribute("from_port", "\"number\"", false, true, false, false, false)))
                    .setNesting(Schema.NestedBlock.NestingMode.LIST)
                    .build();
            var schema = Schema.newBuilder()
                    .setBlock(Schema.Block.newBuilder()
                            .addBlockTypes(authBlock)
                            .addBlockTypes(ruleBlock))
                    .build();

            var apiSchema = convert(schema);

            var auth = property(apiSchema, "masterAuth");
            assertEquals("MasterAuth", auth.type());
            assertTrue(auth.isSensitive(), "block holding a sensitive attribute must be sensitive");
            var ingress = property(apiSchema, "ingress");
            assertEquals("Ingress[]", ingress.type());
            assertFalse(ingress.isSensitive());
        }

        @Test
        @DisplayName("should flatten GROUP blocks into the parent, keeping per-attribute sensitivity")
        void shouldFlattenGroupBlocks() {
            var groupBlock = Schema.NestedBlock.newBuilder()
                    .setTypeName("timeouts")
                    .setBlock(Schema.Block.newBuilder()
                            .addAttributes(attribute("api_token", "\"string\"", false, true, false, true, false))
                            .addAttributes(attribute("create", "\"string\"", false, true, false, false, false)))
                    .setNesting(Schema.NestedBlock.NestingMode.GROUP)
                    .build();
            var schema = Schema.newBuilder()
                    .setBlock(Schema.Block.newBuilder().addBlockTypes(groupBlock))
                    .build();

            var apiSchema = convert(schema);

            assertTrue(property(apiSchema, "apiToken").isSensitive(),
                    "flattened GROUP attribute keeps its own sensitivity");
            assertFalse(property(apiSchema, "create").isSensitive());
        }
    }

    // ---------------------------------------------------------------
    // Test helpers
    // ---------------------------------------------------------------

    /**
     * Convert a JSON string to bytes for cty type representation.
     */
    private static byte[] jsonBytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Build a simple Schema with a single block containing the given attributes.
     */
    private static Schema buildSchema(Schema.Attribute... attributes) {
        var blockBuilder = Schema.Block.newBuilder();
        for (var attr : attributes) {
            blockBuilder.addAttributes(attr);
        }
        return Schema.newBuilder().setBlock(blockBuilder).build();
    }

    /**
     * Build an Attribute proto with the specified flags.
     */
    private static Schema.Attribute attribute(
            String name, String typeJson,
            boolean required, boolean optional, boolean computed,
            boolean sensitive, boolean writeOnly
    ) {
        return Schema.Attribute.newBuilder()
                .setName(name)
                .setType(ByteString.copyFrom(typeJson, StandardCharsets.UTF_8))
                .setRequired(required)
                .setOptional(optional)
                .setComputed(computed)
                .setSensitive(sensitive)
                .setWriteOnly(writeOnly)
                .build();
    }
}
