package cloud.kitelang.provider.aws.networking;

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

    @Property(description = "The IPv4 CIDR block for the VPC (e.g., 10.0.0.0/16)", optional = false)
    private String cidrBlock;

    @Property(description = "The IPv6 CIDR block for the VPC. If specified, AWS associates an IPv6 CIDR block")
    private String ipv6CidrBlock;

    @Property(description = "Tenancy option for instances",
              validValues = {"default", "dedicated"})
    private String instanceTenancy = "default";

    @Property(description = "Enable DNS support in the VPC")
    private Boolean enableDnsSupport = true;

    @Property(description = "Enable DNS hostnames in the VPC")
    private Boolean enableDnsHostnames = false;

    @Property(description = "Tags to apply to the VPC")
    private java.util.Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    @Cloud
    @Property(description = "The VPC ID assigned by AWS")
    private String vpcId;

    @Cloud
    @Property(description = "The current state of the VPC (pending, available)")
    private String state;

    @Cloud
    @Property(description = "The ID of the main route table")
    private String mainRouteTableId;

    @Cloud
    @Property(description = "The ID of the default network ACL")
    private String defaultNetworkAclId;

    @Cloud
    @Property(description = "The ID of the default security group")
    private String defaultSecurityGroupId;

    @Cloud
    @Property(description = "The AWS account ID that owns the VPC")
    private String ownerId;

    @Cloud
    @Property(description = "The Amazon Resource Name (ARN) of the VPC")
    private String arn;
}
