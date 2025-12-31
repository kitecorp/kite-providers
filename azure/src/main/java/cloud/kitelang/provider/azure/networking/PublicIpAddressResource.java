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
 * Azure Public IP Address - a static or dynamic public IPv4/IPv6 address.
 *
 * Example usage:
 * <pre>
 * resource PublicIpAddress nat {
 *     name = "nat-pip"
 *     resourceGroup = main.name
 *     location = main.location
 *     sku = "Standard"
 *     allocationMethod = "Static"
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
@TypeName("PublicIpAddress")
public class PublicIpAddressResource {

    @Property(description = "The name of the public IP address", optional = false)
    private String name;

    @Property(description = "The Azure resource group name", optional = false)
    private String resourceGroup;

    @Property(description = "The Azure region/location", optional = false)
    private String location;

    @Property(description = "The SKU. Standard is required for zone redundancy and NAT Gateway",
              validValues = {"Basic", "Standard"})
    private String sku = "Basic";

    @Property(description = "The allocation method. Standard SKU requires Static",
              validValues = {"Static", "Dynamic"})
    private String allocationMethod = "Dynamic";

    @Property(description = "The IP version",
              validValues = {"IPv4", "IPv6"})
    private String ipVersion = "IPv4";

    @Property(description = "The idle timeout in minutes. Range: 4-30")
    private Integer idleTimeoutInMinutes = 4;

    @Property(description = "The domain name label for DNS. Creates: {label}.{region}.cloudapp.azure.com")
    private String domainNameLabel;

    @Property(description = "The availability zones for the IP. Only for Standard SKU")
    private List<String> zones;

    @Property(description = "Tags to apply to the public IP address")
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    @Cloud(importable = true)
    @Property(description = "The Azure resource ID of the public IP")
    private String id;

    @Cloud
    @Property(description = "The assigned IP address")
    private String ipAddress;

    @Cloud
    @Property(description = "The provisioning state of the public IP")
    private String provisioningState;

    @Cloud
    @Property(description = "The resource GUID")
    private String resourceGuid;

    @Cloud
    @Property(description = "The FQDN of the DNS record")
    private String fqdn;
}
