package cloud.kitelang.provider.azure.compute;

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
 * Azure Virtual Machine - a virtual server in the cloud.
 *
 * Example usage:
 * <pre>
 * resource VirtualMachine web {
 *     name = "web-vm"
 *     resourceGroup = main.name
 *     location = main.location
 *     size = "Standard_B2s"
 *     adminUsername = "azureuser"
 *     sshPublicKey = "ssh-rsa AAAA..."
 *     imagePublisher = "Canonical"
 *     imageOffer = "0001-com-ubuntu-server-jammy"
 *     imageSku = "22_04-lts-gen2"
 *     subnetId = subnet.id
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
@TypeName("VirtualMachine")
public class VirtualMachineResource {

    @Property(description = "The name of the virtual machine", optional = false)
    private String name;

    @Property(description = "The Azure resource group name", optional = false)
    private String resourceGroup;

    @Property(description = "The Azure region/location", optional = false)
    private String location;

    @Property(description = "The VM size (e.g., Standard_B2s, Standard_D2s_v3)", optional = false)
    private String size;

    @Property(description = "The admin username for the VM", optional = false)
    private String adminUsername;

    @Property(description = "The admin password (for Windows or password auth)")
    private String adminPassword;

    @Property(description = "The SSH public key for Linux VMs")
    private String sshPublicKey;

    @Property(description = "The image publisher (e.g., Canonical, MicrosoftWindowsServer)", optional = false)
    private String imagePublisher;

    @Property(description = "The image offer (e.g., 0001-com-ubuntu-server-jammy, WindowsServer)", optional = false)
    private String imageOffer;

    @Property(description = "The image SKU (e.g., 22_04-lts-gen2, 2022-datacenter-azure-edition)", optional = false)
    private String imageSku;

    @Property(description = "The image version")
    private String imageVersion = "latest";

    @Property(description = "The subnet ID to attach the VM to", optional = false)
    private String subnetId;

    @Property(description = "The public IP address ID to associate")
    private String publicIpAddressId;

    @Property(description = "The network security group ID for the NIC")
    private String networkSecurityGroupId;

    @Property(description = "The OS disk size in GB")
    private Integer osDiskSizeGb;

    @Property(description = "The OS disk type",
              validValues = {"Standard_LRS", "StandardSSD_LRS", "Premium_LRS", "UltraSSD_LRS"})
    private String osDiskType = "Premium_LRS";

    @Property(description = "The OS disk caching",
              validValues = {"None", "ReadOnly", "ReadWrite"})
    private String osDiskCaching = "ReadWrite";

    @Property(description = "The computer name (hostname). Defaults to VM name")
    private String computerName;

    @Property(description = "Custom data for cloud-init (Linux) or CustomScriptExtension (Windows)")
    private String customData;

    @Property(description = "Availability zones for the VM")
    private List<String> zones;

    @Property(description = "Tags to apply to the VM and its resources")
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    @Cloud
    @Property(description = "The Azure resource ID of the VM")
    private String id;

    @Cloud
    @Property(description = "The VM unique ID")
    private String vmId;

    @Cloud
    @Property(description = "The provisioning state")
    private String provisioningState;

    @Cloud
    @Property(description = "The power state (running, deallocated, etc.)")
    private String powerState;

    @Cloud
    @Property(description = "The private IP address")
    private String privateIp;

    @Cloud
    @Property(description = "The public IP address (if assigned)")
    private String publicIp;

    @Cloud
    @Property(description = "The network interface ID")
    private String networkInterfaceId;

    @Cloud
    @Property(description = "The OS disk name")
    private String osDiskName;
}
