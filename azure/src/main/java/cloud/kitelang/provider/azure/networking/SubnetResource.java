package cloud.kitelang.provider.azure.networking;

import cloud.kitelang.api.annotations.Cloud;
import cloud.kitelang.api.annotations.Property;
import cloud.kitelang.api.annotations.TypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Azure Subnet - a range of IP addresses in a Virtual Network.
 *
 * Example usage:
 * <pre>
 * resource Subnet public {
 *     name = "public-subnet"
 *     resourceGroup = main.name
 *     vnetName = vnet.name
 *     addressPrefix = "10.0.1.0/24"
 *     networkSecurityGroupId = nsg.id
 * }
 * </pre>
 *
 * @see <a href="https://learn.microsoft.com/en-us/azure/virtual-network/virtual-network-manage-subnet">Azure Subnet Documentation</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("Subnet")
public class SubnetResource {

    @Property(description = "The name of the subnet", optional = false)
    private String name;

    @Property(description = "The Azure resource group name", optional = false)
    private String resourceGroup;

    @Property(description = "The name of the parent Virtual Network", optional = false)
    private String vnetName;

    @Property(description = "The address prefix for the subnet in CIDR notation (e.g., 10.0.1.0/24)", optional = false)
    private String addressPrefix;

    @Property(description = "The ID of the Network Security Group to associate")
    private String networkSecurityGroupId;

    @Property(description = "The ID of the Route Table to associate")
    private String routeTableId;

    @Property(description = "Enable private endpoint network policies")
    private Boolean privateEndpointNetworkPolicies = true;

    @Property(description = "Enable private link service network policies")
    private Boolean privateLinkServiceNetworkPolicies = true;

    @Property(description = "Service endpoints to enable (e.g., Microsoft.Storage, Microsoft.Sql)")
    private java.util.List<String> serviceEndpoints;

    // --- Cloud-managed properties (read-only) ---

    @Cloud(importable = true)
    @Property(description = "The Azure resource ID of the subnet")
    private String id;

    @Cloud
    @Property(description = "The provisioning state of the subnet")
    private String provisioningState;

    @Cloud
    @Property(description = "The purpose of the subnet (e.g., for private endpoints)")
    private String purpose;
}
