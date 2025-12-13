package cloud.kitelang.provider.azure;

import cloud.kitelang.api.annotations.Cloud;
import cloud.kitelang.api.annotations.Property;
import cloud.kitelang.api.annotations.TypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    /**
     * The name of the public IP address.
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
     * The SKU: "Basic" or "Standard".
     * Standard is required for zone redundancy and NAT Gateway.
     * Default: "Basic"
     */
    @Property
    private String sku;

    /**
     * The allocation method: "Static" or "Dynamic".
     * Standard SKU requires Static.
     * Default: "Dynamic"
     */
    @Property
    private String allocationMethod;

    /**
     * The IP version: "IPv4" or "IPv6".
     * Default: "IPv4"
     */
    @Property
    private String ipVersion;

    /**
     * The idle timeout in minutes.
     * Range: 4-30. Default: 4
     */
    @Property
    private Integer idleTimeoutInMinutes;

    /**
     * The domain name label for DNS.
     * Creates: {label}.{region}.cloudapp.azure.com
     */
    @Property
    private String domainNameLabel;

    /**
     * The availability zones for the IP.
     * Only for Standard SKU.
     */
    @Property
    private java.util.List<String> zones;

    /**
     * Tags to apply to the public IP address.
     */
    @Property
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    /**
     * The Azure resource ID of the public IP.
     */
    @Cloud
    @Property
    private String id;

    /**
     * The assigned IP address.
     */
    @Cloud
    @Property
    private String ipAddress;

    /**
     * The provisioning state of the public IP.
     */
    @Cloud
    @Property
    private String provisioningState;

    /**
     * The resource GUID.
     */
    @Cloud
    @Property
    private String resourceGuid;

    /**
     * The FQDN of the DNS record.
     */
    @Cloud
    @Property
    private String fqdn;
}
