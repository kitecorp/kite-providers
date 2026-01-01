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
 * Azure Network Security Group (NSG).
 * Controls inbound and outbound traffic to network interfaces, VMs, and subnets.
 * Equivalent to AWS Security Group.
 *
 * Example usage:
 * <pre>
 * resource NetworkSecurityGroup web {
 *     name = "web-nsg"
 *     resourceGroup = rg.name
 *     location = "eastus"
 *     securityRules = [
 *         {
 *             name: "allow-http",
 *             priority: 100,
 *             direction: "Inbound",
 *             access: "Allow",
 *             protocol: "Tcp",
 *             sourceAddressPrefix: "*",
 *             sourcePortRange: "*",
 *             destinationAddressPrefix: "*",
 *             destinationPortRange: "80"
 *         },
 *         {
 *             name: "allow-https",
 *             priority: 110,
 *             direction: "Inbound",
 *             access: "Allow",
 *             protocol: "Tcp",
 *             sourceAddressPrefix: "*",
 *             sourcePortRange: "*",
 *             destinationAddressPrefix: "*",
 *             destinationPortRange: "443"
 *         }
 *     ]
 *     tags = {
 *         Environment: "production"
 *     }
 * }
 * </pre>
 *
 * @see <a href="https://learn.microsoft.com/en-us/azure/virtual-network/network-security-groups-overview">Azure Network Security Group Documentation</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("NetworkSecurityGroup")
public class NetworkSecurityGroupResource {

    @Property(description = "The name of the network security group", optional = false)
    private String name;

    @Property(description = "The Azure resource group name", optional = false)
    private String resourceGroup;

    @Property(description = "The Azure region/location", optional = false)
    private String location;

    @Property(description = "List of security rules. Each rule defines allowed or denied traffic")
    private List<SecurityRule> securityRules;

    @Property(description = "Tags to apply to the NSG")
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    @Cloud(importable = true)
    @Property(description = "The Azure resource ID")
    private String id;

    @Cloud
    @Property(description = "The provisioning state")
    private String provisioningState;

    @Cloud
    @Property(description = "The resource GUID")
    private String resourceGuid;

    @Cloud
    @Property(description = "List of network interfaces associated with this NSG")
    private List<String> networkInterfaceIds;

    @Cloud
    @Property(description = "List of subnets associated with this NSG")
    private List<String> subnetIds;
}
