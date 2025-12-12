package cloud.kitelang.provider.aws;

import cloud.kitelang.api.annotations.Cloud;
import cloud.kitelang.api.annotations.Property;
import cloud.kitelang.api.annotations.TypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AWS VPC resource.
 *
 * Example usage:
 * <pre>
 * resource Vpc main {
 *     cidr_block = "10.0.0.0/16"
 *     enable_dns_support = true
 *     enable_dns_hostnames = true
 *     tags = {
 *         Name: "my-vpc"
 *         Environment: "production"
 *     }
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("Vpc")
public class VpcResource {

    /**
     * The IPv4 CIDR block for the VPC.
     * Required.
     */
    @Property
    private String cidrBlock;

    /**
     * The IPv6 CIDR block for the VPC.
     * Optional - if specified, AWS will associate an IPv6 CIDR block.
     */
    @Property
    private String ipv6CidrBlock;

    /**
     * The tenancy option for instances in the VPC.
     * Valid values: default, dedicated.
     */
    @Property
    private String instanceTenancy;

    /**
     * Enable DNS support in the VPC.
     * Default: true
     */
    @Property
    private Boolean enableDnsSupport;

    /**
     * Enable DNS hostnames in the VPC.
     * Default: false
     */
    @Property
    private Boolean enableDnsHostnames;

    /**
     * Tags to apply to the VPC.
     */
    @Property
    private java.util.Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    /**
     * The VPC ID assigned by AWS.
     */
    @Cloud
    @Property
    private String vpcId;

    /**
     * The current state of the VPC.
     */
    @Cloud
    @Property
    private String state;

    /**
     * The ID of the main route table.
     */
    @Cloud
    @Property
    private String mainRouteTableId;

    /**
     * The ID of the default network ACL.
     */
    @Cloud
    @Property
    private String defaultNetworkAclId;

    /**
     * The ID of the default security group.
     */
    @Cloud
    @Property
    private String defaultSecurityGroupId;

    /**
     * The AWS account ID that owns the VPC.
     */
    @Cloud
    @Property
    private String ownerId;

    /**
     * The Amazon Resource Name (ARN) of the VPC.
     */
    @Cloud
    @Property
    private String arn;
}
