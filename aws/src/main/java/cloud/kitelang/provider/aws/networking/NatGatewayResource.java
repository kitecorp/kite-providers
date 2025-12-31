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

    @Property(description = "The subnet ID where the NAT gateway will be created")
    private String subnetId;

    @Property(description = "The allocation ID of the Elastic IP for public NAT gateway")
    private String allocationId;

    @Property(description = "The connectivity type: 'public' or 'private'. Default: 'public'")
    private String connectivityType;

    @Property(description = "Tags to apply to the NAT gateway")
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    @Cloud
    @Property(description = "The NAT gateway ID assigned by AWS")
    private String natGatewayId;

    @Cloud
    @Property(description = "The current state of the NAT gateway")
    private String state;

    @Cloud
    @Property(description = "The VPC ID that contains the NAT gateway")
    private String vpcId;

    @Cloud
    @Property(description = "The public IP address associated with the NAT gateway")
    private String publicIp;

    @Cloud
    @Property(description = "The private IP address of the NAT gateway")
    private String privateIp;
}
