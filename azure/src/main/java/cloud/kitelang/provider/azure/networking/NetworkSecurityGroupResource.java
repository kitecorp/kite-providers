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
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("NetworkSecurityGroup")
public class NetworkSecurityGroupResource {

    /**
     * The name of the network security group.
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
     * List of security rules.
     * Each rule defines allowed or denied traffic.
     */
    @Property
    private List<SecurityRule> securityRules;

    /**
     * Tags to apply to the NSG.
     */
    @Property
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    /**
     * The Azure resource ID.
     */
    @Cloud
    @Property
    private String id;

    /**
     * The provisioning state.
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
     * List of network interfaces associated with this NSG.
     */
    @Cloud
    @Property
    private List<String> networkInterfaceIds;

    /**
     * List of subnets associated with this NSG.
     */
    @Cloud
    @Property
    private List<String> subnetIds;
}
