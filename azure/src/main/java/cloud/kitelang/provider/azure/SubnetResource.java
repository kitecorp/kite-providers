package cloud.kitelang.provider.azure;

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
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("Subnet")
public class SubnetResource {

    /**
     * The name of the subnet.
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
     * The name of the parent Virtual Network.
     * Required.
     */
    @Property
    private String vnetName;

    /**
     * The address prefix for the subnet in CIDR notation.
     * Required. Example: "10.0.1.0/24"
     */
    @Property
    private String addressPrefix;

    /**
     * The ID of the Network Security Group to associate.
     * Optional.
     */
    @Property
    private String networkSecurityGroupId;

    /**
     * The ID of the Route Table to associate.
     * Optional.
     */
    @Property
    private String routeTableId;

    /**
     * Enable private endpoint network policies.
     * Default: true
     */
    @Property
    private Boolean privateEndpointNetworkPolicies;

    /**
     * Enable private link service network policies.
     * Default: true
     */
    @Property
    private Boolean privateLinkServiceNetworkPolicies;

    /**
     * Service endpoints to enable on the subnet.
     * Example: ["Microsoft.Storage", "Microsoft.Sql"]
     */
    @Property
    private java.util.List<String> serviceEndpoints;

    // --- Cloud-managed properties (read-only) ---

    /**
     * The Azure resource ID of the subnet.
     */
    @Cloud
    @Property
    private String id;

    /**
     * The provisioning state of the subnet.
     */
    @Cloud
    @Property
    private String provisioningState;

    /**
     * The purpose of the subnet (e.g., for private endpoints).
     */
    @Cloud
    @Property
    private String purpose;
}
