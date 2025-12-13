package cloud.kitelang.provider.azure;

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
 * Azure Route Table - controls routing for subnets.
 *
 * Example usage:
 * <pre>
 * resource RouteTable custom {
 *     name = "custom-routes"
 *     resourceGroup = main.name
 *     location = main.location
 *     disableBgpRoutePropagation = false
 *     routes = [
 *         {
 *             name: "to-firewall",
 *             addressPrefix: "0.0.0.0/0",
 *             nextHopType: "VirtualAppliance",
 *             nextHopIpAddress: "10.0.1.4"
 *         }
 *     ]
 *     tags = {
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
     * The name of the route table.
     * Required.
     */
    @Property
    private String name;

    /**
     * The Azure resource group name.
     * Required.
     */
    @Property
    private String resourceGroup;

    /**
     * The Azure region/location.
     * Required.
     */
    @Property
    private String location;

    /**
     * Whether to disable BGP route propagation.
     * Default: false
     */
    @Property
    private Boolean disableBgpRoutePropagation;

    /**
     * Routes in this route table.
     */
    @Property
    private List<RouteEntry> routes;

    /**
     * Tags to apply to the route table.
     */
    @Property
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    /**
     * The Azure resource ID of the route table.
     */
    @Cloud
    @Property
    private String id;

    /**
     * The provisioning state of the route table.
     */
    @Cloud
    @Property
    private String provisioningState;

    /**
     * List of subnet IDs associated with this route table.
     */
    @Cloud
    @Property
    private List<String> associatedSubnetIds;
}
