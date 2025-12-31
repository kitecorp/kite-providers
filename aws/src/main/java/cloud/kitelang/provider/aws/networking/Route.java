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
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("Route")
public class Route {

    /**
     * The IPv4 CIDR block for the route destination.
     */
    @Property
    private String destinationCidrBlock;

    /**
     * The IPv6 CIDR block for the route destination.
     */
    @Property
    private String destinationIpv6CidrBlock;

    /**
     * The ID of an internet gateway.
     */
    @Property
    private String gatewayId;

    /**
     * The ID of a NAT gateway.
     */
    @Property
    private String natGatewayId;

    /**
     * The ID of a network interface.
     */
    @Property
    private String networkInterfaceId;

    /**
     * The ID of a VPC peering connection.
     */
    @Property
    private String vpcPeeringConnectionId;

    /**
     * The ID of a transit gateway.
     */
    @Property
    private String transitGatewayId;

    /**
     * The ID of a VPC endpoint.
     */
    @Property
    private String vpcEndpointId;

    /**
     * The ID of an egress-only internet gateway.
     */
    @Property
    private String egressOnlyInternetGatewayId;
}
