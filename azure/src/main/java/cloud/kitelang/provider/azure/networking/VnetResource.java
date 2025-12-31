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
 * Azure Virtual Network (VNet) resource.
 * Equivalent to AWS VPC.
 *
 * Example usage:
 * <pre>
 * resource Vnet main {
 *     name = "main-vnet"
 *     resourceGroup = "my-resource-group"
 *     location = "eastus"
 *     addressSpaces = ["10.0.0.0/16", "172.16.0.0/16"]
 *     dnsServers = ["10.0.0.4"]
 *     tags = {
 *         Environment: "production"
 *         ManagedBy: "kite"
 *     }
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("Vnet")
public class VnetResource {

    /**
     * The name of the virtual network.
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
     * The Azure region/location (e.g., "eastus", "westeurope").
     * Required.
     */
    @Property
    private String location;

    /**
     * The address spaces for the VNet in CIDR notation.
     * At least one is required.
     * Example: ["10.0.0.0/16", "172.16.0.0/16"]
     */
    @Property
    private List<String> addressSpaces;

    /**
     * Custom DNS servers for the VNet.
     * If not specified, Azure-provided DNS is used.
     */
    @Property
    private List<String> dnsServers;

    /**
     * Enable DDoS protection for the VNet.
     * Default: false (Basic protection)
     */
    @Property
    private Boolean enableDdosProtection;

    /**
     * Enable VM protection for the VNet.
     * Default: false
     */
    @Property
    private Boolean enableVmProtection;

    /**
     * Tags to apply to the VNet.
     */
    @Property
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    /**
     * The Azure resource ID of the VNet.
     */
    @Cloud
    @Property
    private String id;

    /**
     * The provisioning state of the VNet.
     */
    @Cloud
    @Property
    private String provisioningState;

    /**
     * The resource GUID of the VNet.
     */
    @Cloud
    @Property
    private String resourceGuid;

    /**
     * Whether the VNet has any subnets.
     */
    @Cloud
    @Property
    private Boolean hasSubnets;
}
