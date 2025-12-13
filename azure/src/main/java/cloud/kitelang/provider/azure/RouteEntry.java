package cloud.kitelang.provider.azure;

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
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("RouteEntry")
public class RouteEntry {

    /**
     * The name of the route.
     * Required.
     */
    @Property
    private String name;

    /**
     * The destination CIDR to which the route applies.
     * Required. Example: "0.0.0.0/0" or "10.0.0.0/8"
     */
    @Property
    private String addressPrefix;

    /**
     * The type of next hop.
     * Valid values: VirtualNetworkGateway, VnetLocal, Internet, VirtualAppliance, None
     */
    @Property
    private String nextHopType;

    /**
     * The IP address of the next hop.
     * Required when nextHopType is VirtualAppliance.
     */
    @Property
    private String nextHopIpAddress;
}
