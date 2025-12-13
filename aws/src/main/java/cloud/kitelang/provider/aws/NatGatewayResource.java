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
 * AWS NAT Gateway - enables instances in private subnets to connect to the internet.
 *
 * Example usage:
 * <pre>
 * resource NatGateway main {
 *     subnetId = publicSubnet.subnetId
 *     allocationId = eip.allocationId
 *     connectivityType = "public"
 *     tags = {
 *         Name: "main-nat",
 *         Environment: "production"
 *     }
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("NatGateway")
public class NatGatewayResource {

    /**
     * The subnet ID where the NAT gateway will be created.
     * For public NAT, must be a public subnet.
     * Required.
     */
    @Property
    private String subnetId;

    /**
     * The allocation ID of the Elastic IP for public NAT gateway.
     * Required for public connectivity type.
     */
    @Property
    private String allocationId;

    /**
     * The connectivity type: "public" or "private".
     * Default: "public"
     */
    @Property
    private String connectivityType;

    /**
     * Tags to apply to the NAT gateway.
     */
    @Property
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    /**
     * The NAT gateway ID assigned by AWS.
     */
    @Cloud
    @Property
    private String natGatewayId;

    /**
     * The current state of the NAT gateway.
     * Values: pending, available, deleting, deleted, failed
     */
    @Cloud
    @Property
    private String state;

    /**
     * The VPC ID that contains the NAT gateway.
     */
    @Cloud
    @Property
    private String vpcId;

    /**
     * The public IP address associated with the NAT gateway.
     */
    @Cloud
    @Property
    private String publicIp;

    /**
     * The private IP address of the NAT gateway.
     */
    @Cloud
    @Property
    private String privateIp;
}
