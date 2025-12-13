package cloud.kitelang.provider.azure;

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

    /**
     * The name of the virtual machine.
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
     * The VM size (e.g., Standard_B2s, Standard_D2s_v3).
     * Required.
     */
    @Property
    private String size;

    /**
     * The admin username for the VM.
     * Required.
     */
    @Property
    private String adminUsername;

    /**
     * The admin password (for Windows or password auth).
     * Either adminPassword or sshPublicKey is required.
     */
    @Property
    private String adminPassword;

    /**
     * The SSH public key for Linux VMs.
     * Either adminPassword or sshPublicKey is required.
     */
    @Property
    private String sshPublicKey;

    /**
     * The image publisher (e.g., Canonical, MicrosoftWindowsServer).
     * Required.
     */
    @Property
    private String imagePublisher;

    /**
     * The image offer (e.g., 0001-com-ubuntu-server-jammy, WindowsServer).
     * Required.
     */
    @Property
    private String imageOffer;

    /**
     * The image SKU (e.g., 22_04-lts-gen2, 2022-datacenter-azure-edition).
     * Required.
     */
    @Property
    private String imageSku;

    /**
     * The image version.
     * Default: "latest"
     */
    @Property
    private String imageVersion;

    /**
     * The subnet ID to attach the VM to.
     * Required.
     */
    @Property
    private String subnetId;

    /**
     * The public IP address ID to associate.
     */
    @Property
    private String publicIpAddressId;

    /**
     * The network security group ID for the NIC.
     */
    @Property
    private String networkSecurityGroupId;

    /**
     * The OS disk size in GB.
     */
    @Property
    private Integer osDiskSizeGb;

    /**
     * The OS disk type: Standard_LRS, StandardSSD_LRS, Premium_LRS, UltraSSD_LRS.
     * Default: Premium_LRS
     */
    @Property
    private String osDiskType;

    /**
     * The OS disk caching: None, ReadOnly, ReadWrite.
     * Default: ReadWrite
     */
    @Property
    private String osDiskCaching;

    /**
     * The computer name (hostname).
     * Default: same as VM name
     */
    @Property
    private String computerName;

    /**
     * Custom data for cloud-init (Linux) or CustomScriptExtension (Windows).
     * Will be base64 encoded.
     */
    @Property
    private String customData;

    /**
     * Availability zones for the VM.
     */
    @Property
    private List<String> zones;

    /**
     * Tags to apply to the VM and its resources.
     */
    @Property
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    /**
     * The Azure resource ID of the VM.
     */
    @Cloud
    @Property
    private String id;

    /**
     * The VM unique ID.
     */
    @Cloud
    @Property
    private String vmId;

    /**
     * The provisioning state.
     */
    @Cloud
    @Property
    private String provisioningState;

    /**
     * The power state (running, deallocated, etc.).
     */
    @Cloud
    @Property
    private String powerState;

    /**
     * The private IP address.
     */
    @Cloud
    @Property
    private String privateIp;

    /**
     * The public IP address (if assigned).
     */
    @Cloud
    @Property
    private String publicIp;

    /**
     * The network interface ID.
     */
    @Cloud
    @Property
    private String networkInterfaceId;

    /**
     * The OS disk name.
     */
    @Cloud
    @Property
    private String osDiskName;
}
