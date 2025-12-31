package cloud.kitelang.provider.aws.networking;

import cloud.kitelang.api.annotations.Cloud;
import cloud.kitelang.api.annotations.Property;
import cloud.kitelang.api.annotations.TypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * AWS Subnet - a range of IP addresses in a VPC.
 *
 * Example usage:
 * <pre>
 * resource Subnet public {
 *     vpcId = vpc.vpcId
 *     cidrBlock = "10.0.1.0/24"
 *     availabilityZone = "us-east-1a"
 *     mapPublicIpOnLaunch = true
 *     tags = {
 *         Name: "public-subnet",
 *         Environment: "production"
 *     }
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("Subnet")
public class SubnetResource {

    @Property(description = "The VPC ID where the subnet will be created", optional = false)
    private String vpcId;

    @Property(description = "The IPv4 CIDR block for the subnet (e.g., 10.0.1.0/24)", optional = false)
    private String cidrBlock;

    @Property(description = "The IPv6 CIDR block for the subnet")
    private String ipv6CidrBlock;

    @Property(description = "The Availability Zone for the subnet (e.g., us-east-1a)")
    private String availabilityZone;

    @Property(description = "Auto-assign public IPv4 addresses to instances")
    private Boolean mapPublicIpOnLaunch = false;

    @Property(description = "Assign IPv6 addresses to instances")
    private Boolean assignIpv6AddressOnCreation = false;

    @Property(description = "Tags to apply to the subnet")
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    @Cloud
    @Property(description = "The subnet ID assigned by AWS")
    private String subnetId;

    @Cloud
    @Property(description = "The current state of the subnet (pending, available)")
    private String state;

    @Cloud
    @Property(description = "The Availability Zone ID")
    private String availabilityZoneId;

    @Cloud
    @Property(description = "The number of available IPv4 addresses")
    private Integer availableIpAddressCount;

    @Cloud
    @Property(description = "Whether this is the default subnet for the AZ")
    private Boolean defaultForAz;

    @Cloud
    @Property(description = "The AWS account ID that owns the subnet")
    private String ownerId;

    @Cloud
    @Property(description = "The Amazon Resource Name (ARN) of the subnet")
    private String arn;
}
