package cloud.kitelang.provider.azure.dns;

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
 *
 * @see <a href="https://learn.microsoft.com/en-us/azure/dns/dns-zones-records">Azure DNS Zone Documentation</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("DnsZone")
public class DnsZoneResource {

    @Property(description = "The domain name for the DNS zone. Must be a fully qualified domain name (e.g., example.com)", optional = false)
    private String name;

    @Property(description = "The resource group name", optional = false)
    private String resourceGroup;

    @Property(description = "The type of DNS zone",
              validValues = {"Public", "Private"})
    private String zoneType = "Public";

    @Property(description = "Virtual network IDs for auto-registration (private zones only). VMs in these networks will have their DNS records auto-registered")
    private List<String> registrationVirtualNetworkIds;

    @Property(description = "Virtual network IDs for resolution (private zones only). VMs in these networks can resolve records in this zone")
    private List<String> resolutionVirtualNetworkIds;

    @Property(description = "Tags to apply to the DNS zone")
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    @Cloud(importable = true)
    @Property(description = "The resource ID of the DNS zone")
    private String id;

    @Cloud
    @Property(description = "The name servers for the DNS zone")
    private List<String> nameServers;

    @Cloud
    @Property(description = "The number of record sets in the DNS zone")
    private Long numberOfRecordSets;

    @Cloud
    @Property(description = "The maximum number of record sets allowed")
    private Long maxNumberOfRecordSets;

    @Cloud
    @Property(description = "The ETag of the DNS zone")
    private String etag;
}
