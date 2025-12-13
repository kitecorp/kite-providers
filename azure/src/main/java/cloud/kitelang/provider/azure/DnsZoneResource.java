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
 * Azure DNS Zone resource.
 *
 * Example usage:
 * <pre>
 * resource DnsZone main {
 *     name = "example.com"
 *     resourceGroup = rg.name
 *     tags = {
 *         Environment: "production"
 *     }
 * }
 *
 * resource DnsZone private {
 *     name = "internal.example.com"
 *     resourceGroup = rg.name
 *     zoneType = "Private"
 *     registrationVirtualNetworkIds = [vnet.id]
 *     resolutionVirtualNetworkIds = [vnet2.id]
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("DnsZone")
public class DnsZoneResource {

    /**
     * The domain name for the DNS zone.
     * Must be a fully qualified domain name (e.g., "example.com").
     * Required.
     */
    @Property
    private String name;

    /**
     * The resource group name.
     * Required.
     */
    @Property
    private String resourceGroup;

    /**
     * The type of DNS zone.
     * Valid values: Public, Private.
     * Default: Public
     */
    @Property
    private String zoneType;

    /**
     * Virtual network IDs for auto-registration (private zones only).
     * VMs in these networks will have their DNS records auto-registered.
     */
    @Property
    private List<String> registrationVirtualNetworkIds;

    /**
     * Virtual network IDs for resolution (private zones only).
     * VMs in these networks can resolve records in this zone.
     */
    @Property
    private List<String> resolutionVirtualNetworkIds;

    /**
     * Tags to apply to the DNS zone.
     */
    @Property
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    /**
     * The resource ID of the DNS zone.
     */
    @Cloud
    @Property
    private String id;

    /**
     * The name servers for the DNS zone.
     */
    @Cloud
    @Property
    private List<String> nameServers;

    /**
     * The number of record sets in the DNS zone.
     */
    @Cloud
    @Property
    private Long numberOfRecordSets;

    /**
     * The maximum number of record sets allowed.
     */
    @Cloud
    @Property
    private Long maxNumberOfRecordSets;

    /**
     * The ETag of the DNS zone.
     */
    @Cloud
    @Property
    private String etag;
}
