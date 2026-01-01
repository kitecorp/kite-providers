package cloud.kitelang.provider.aws.networking;

import cloud.kitelang.api.annotations.Property;
import cloud.kitelang.api.annotations.TypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A route entry for AWS Route Tables.
 *
 * Example:
 * <pre>
 * {
 *     destinationCidrBlock: "0.0.0.0/0",
 *     gatewayId: igw.internetGatewayId
 * }
 * </pre>
 *
 * @see <a href="https://docs.aws.amazon.com/vpc/latest/userguide/VPC_Route_Tables.html">AWS Route Table Documentation</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("Route")
public class Route {

    @Property(description = "The IPv4 CIDR block for the route destination")
    private String destinationCidrBlock;

    @Property(description = "The IPv6 CIDR block for the route destination")
    private String destinationIpv6CidrBlock;

    @Property(description = "The ID of an internet gateway")
    private String gatewayId;

    @Property(description = "The ID of a NAT gateway")
    private String natGatewayId;

    @Property(description = "The ID of a network interface")
    private String networkInterfaceId;

    @Property(description = "The ID of a VPC peering connection")
    private String vpcPeeringConnectionId;

    @Property(description = "The ID of a transit gateway")
    private String transitGatewayId;

    @Property(description = "The ID of a VPC endpoint")
    private String vpcEndpointId;

    @Property(description = "The ID of an egress-only internet gateway")
    private String egressOnlyInternetGatewayId;
}
