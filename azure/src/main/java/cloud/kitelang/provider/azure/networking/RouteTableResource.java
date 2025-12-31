package cloud.kitelang.provider.azure.networking;

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

    @Property(description = "The name of the route table", optional = false)
    private String name;

    @Property(description = "The Azure resource group name", optional = false)
    private String resourceGroup;

    @Property(description = "The Azure region/location", optional = false)
    private String location;

    @Property(description = "Whether to disable BGP route propagation")
    private Boolean disableBgpRoutePropagation = false;

    @Property(description = "Routes in this route table")
    private List<RouteEntry> routes;

    @Property(description = "Tags to apply to the route table")
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    @Cloud(importable = true)
    @Property(description = "The Azure resource ID of the route table")
    private String id;

    @Cloud
    @Property(description = "The provisioning state of the route table")
    private String provisioningState;

    @Cloud
    @Property(description = "List of subnet IDs associated with this route table")
    private List<String> associatedSubnetIds;
}
