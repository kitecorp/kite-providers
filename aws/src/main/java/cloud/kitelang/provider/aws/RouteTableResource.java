package cloud.kitelang.provider.aws;

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
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("RouteTable")
public class RouteTableResource {

    /**
     * The VPC ID where the route table will be created.
     * Required.
     */
    @Property
    private String vpcId;

    /**
     * Routes in this route table.
     */
    @Property
    private List<Route> routes;

    /**
     * Tags to apply to the route table.
     */
    @Property
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    /**
     * The route table ID assigned by AWS.
     */
    @Cloud
    @Property
    private String routeTableId;

    /**
     * The AWS account ID that owns the route table.
     */
    @Cloud
    @Property
    private String ownerId;

    /**
     * Whether this is the main route table for the VPC.
     */
    @Cloud
    @Property
    private Boolean main;

    /**
     * List of subnet IDs associated with this route table.
     */
    @Cloud
    @Property
    private List<String> associatedSubnetIds;
}
