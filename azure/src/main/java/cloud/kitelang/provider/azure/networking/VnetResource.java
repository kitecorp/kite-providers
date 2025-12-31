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

    @Property(description = "The name of the virtual network", optional = false)
    private String name;

    @Property(description = "The Azure resource group name", optional = false)
    private String resourceGroup;

    @Property(description = "The Azure region/location (e.g., eastus, westeurope)", optional = false)
    private String location;

    @Property(description = "The address spaces for the VNet in CIDR notation", optional = false)
    private List<String> addressSpaces;

    @Property(description = "Custom DNS servers for the VNet. If not specified, Azure-provided DNS is used")
    private List<String> dnsServers;

    @Property(description = "Enable DDoS protection for the VNet")
    private Boolean enableDdosProtection = false;

    @Property(description = "Enable VM protection for the VNet")
    private Boolean enableVmProtection = false;

    @Property(description = "Tags to apply to the VNet")
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    @Cloud
    @Property(description = "The Azure resource ID of the VNet")
    private String id;

    @Cloud
    @Property(description = "The provisioning state of the VNet")
    private String provisioningState;

    @Cloud
    @Property(description = "The resource GUID of the VNet")
    private String resourceGuid;

    @Cloud
    @Property(description = "Whether the VNet has any subnets")
    private Boolean hasSubnets;
}
