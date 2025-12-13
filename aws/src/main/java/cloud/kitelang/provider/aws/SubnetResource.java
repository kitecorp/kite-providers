package cloud.kitelang.provider.aws;

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

    /**
     * The VPC ID where the subnet will be created.
     * Required.
     */
    @Property
    private String vpcId;

    /**
     * The IPv4 CIDR block for the subnet.
     * Required.
     */
    @Property
    private String cidrBlock;

    /**
     * The IPv6 CIDR block for the subnet.
     * Optional.
     */
    @Property
    private String ipv6CidrBlock;

    /**
     * The Availability Zone for the subnet.
     * Optional - if not specified, AWS will choose one.
     */
    @Property
    private String availabilityZone;

    /**
     * Whether to auto-assign public IPv4 addresses to instances launched in this subnet.
     * Default: false
     */
    @Property
    private Boolean mapPublicIpOnLaunch;

    /**
     * Whether to assign IPv6 addresses to instances launched in this subnet.
     * Default: false
     */
    @Property
    private Boolean assignIpv6AddressOnCreation;

    /**
     * Tags to apply to the subnet.
     */
    @Property
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    /**
     * The subnet ID assigned by AWS.
     */
    @Cloud
    @Property
    private String subnetId;

    /**
     * The current state of the subnet (pending, available).
     */
    @Cloud
    @Property
    private String state;

    /**
     * The Availability Zone ID.
     */
    @Cloud
    @Property
    private String availabilityZoneId;

    /**
     * The number of available IPv4 addresses in the subnet.
     */
    @Cloud
    @Property
    private Integer availableIpAddressCount;

    /**
     * Whether this is the default subnet for the AZ.
     */
    @Cloud
    @Property
    private Boolean defaultForAz;

    /**
     * The AWS account ID that owns the subnet.
     */
    @Cloud
    @Property
    private String ownerId;

    /**
     * The Amazon Resource Name (ARN) of the subnet.
     */
    @Cloud
    @Property
    private String arn;
}
