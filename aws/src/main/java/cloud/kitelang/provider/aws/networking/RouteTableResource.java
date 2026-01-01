package cloud.kitelang.provider.aws.networking;

import cloud.kitelang.api.annotations.Cloud;
import cloud.kitelang.api.annotations.Property;
import cloud.kitelang.api.annotations.TypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * AWS Route Table - controls routing for subnets.
 *
 * Example usage:
 * <pre>
 * resource RouteTable public {
 *     vpcId = vpc.vpcId
 *     routes = [
 *         {
 *             destinationCidrBlock: "0.0.0.0/0",
 *             gatewayId: igw.internetGatewayId
 *         }
 *     ]
 *     tags = {
 *         Name: "public-rt",
 *         Environment: "production"
 *     }
 * }
 * </pre>
 *
 * @see <a href="https://docs.aws.amazon.com/vpc/latest/userguide/VPC_Route_Tables.html">AWS Route Table Documentation</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("RouteTable")
public class RouteTableResource {

    @Property(description = "The VPC ID where the route table will be created")
    private String vpcId;

    @Property(description = "Routes in this route table")
    private List<Route> routes;

    @Property(description = "Tags to apply to the route table")
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    @Cloud
    @Property(description = "The route table ID assigned by AWS")
    private String routeTableId;

    @Cloud
    @Property(description = "The AWS account ID that owns the route table")
    private String ownerId;

    @Cloud
    @Property(description = "Whether this is the main route table for the VPC")
    private Boolean main;

    @Cloud
    @Property(description = "List of subnet IDs associated with this route table")
    private List<String> associatedSubnetIds;
}
