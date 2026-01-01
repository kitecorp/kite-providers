package cloud.kitelang.provider.azure.networking;

import cloud.kitelang.api.annotations.Property;
import cloud.kitelang.api.annotations.TypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A route entry for Azure Route Tables.
 *
 * Example:
 * <pre>
 * {
 *     name: "to-internet",
 *     addressPrefix: "0.0.0.0/0",
 *     nextHopType: "VirtualAppliance",
 *     nextHopIpAddress: "10.0.1.4"
 * }
 * </pre>
 *
 * @see <a href="https://learn.microsoft.com/en-us/azure/virtual-network/manage-route-table">Azure Route Table Documentation</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("RouteEntry")
public class RouteEntry {

    @Property(description = "The name of the route", optional = false)
    private String name;

    @Property(description = "The destination CIDR to which the route applies. Example: 0.0.0.0/0 or 10.0.0.0/8", optional = false)
    private String addressPrefix;

    @Property(description = "The type of next hop",
              validValues = {"VirtualNetworkGateway", "VnetLocal", "Internet", "VirtualAppliance", "None"},
              optional = false)
    private String nextHopType;

    @Property(description = "The IP address of the next hop. Required when nextHopType is VirtualAppliance")
    private String nextHopIpAddress;
}
