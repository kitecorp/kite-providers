package cloud.kitelang.provider.azure;

import cloud.kitelang.provider.Diagnostic;
import cloud.kitelang.provider.ResourceTypeHandler;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.Region;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.*;
import com.azure.resourcemanager.network.NetworkManager;
import com.azure.resourcemanager.network.models.NetworkInterface;
import com.azure.resourcemanager.network.models.NetworkSecurityGroup;
import com.azure.resourcemanager.network.models.PublicIpAddress;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * ResourceTypeHandler for Azure Virtual Machine.
 * Implements CRUD operations for VMs using Azure SDK.
 */
@Log4j2
public class VirtualMachineResourceType extends ResourceTypeHandler<VirtualMachineResource> {

    private final ComputeManager computeManager;
    private final NetworkManager networkManager;

    public VirtualMachineResourceType() {
        var credential = new DefaultAzureCredentialBuilder().build();

        String subscriptionId = System.getenv("AZURE_SUBSCRIPTION_ID");
        if (subscriptionId == null || subscriptionId.isBlank()) {
            throw new IllegalStateException(
                    "AZURE_SUBSCRIPTION_ID environment variable must be set");
        }

        String tenantId = System.getenv("AZURE_TENANT_ID");
        var profile = new AzureProfile(tenantId, subscriptionId, AzureEnvironment.AZURE);

        this.computeManager = ComputeManager.authenticate(credential, profile);
        this.networkManager = NetworkManager.authenticate(credential, profile);
    }

    public VirtualMachineResourceType(ComputeManager computeManager, NetworkManager networkManager) {
        this.computeManager = computeManager;
        this.networkManager = networkManager;
    }

    @Override
    public VirtualMachineResource create(VirtualMachineResource resource) {
        log.info("Creating Virtual Machine '{}' in resource group '{}'",
                resource.getName(), resource.getResourceGroup());

        // Create network interface first
        String nicName = resource.getName() + "-nic";
        NetworkInterface nic = createNetworkInterface(nicName, resource);
        log.info("Created network interface: {}", nic.id());

        // Create VM
        VirtualMachine vm = createVirtualMachine(resource, nic);
        log.info("Created Virtual Machine: {}", vm.id());

        return mapToResource(vm, nic);
    }

    private NetworkInterface createNetworkInterface(String nicName, VirtualMachineResource resource) {
        var network = getNetworkFromSubnet(resource.getSubnetId());
        String subnetName = getSubnetName(resource.getSubnetId());

        var nicBuilder = networkManager.networkInterfaces()
                .define(nicName)
                .withRegion(Region.fromName(resource.getLocation()))
                .withExistingResourceGroup(resource.getResourceGroup())
                .withExistingPrimaryNetwork(network)
                .withSubnet(subnetName)
                .withPrimaryPrivateIPAddressDynamic();

        // Associate public IP if provided
        if (resource.getPublicIpAddressId() != null) {
            PublicIpAddress pip = networkManager.publicIpAddresses()
                    .getById(resource.getPublicIpAddressId());
            nicBuilder = nicBuilder.withExistingPrimaryPublicIPAddress(pip);
        }

        // Associate NSG if provided
        if (resource.getNetworkSecurityGroupId() != null) {
            NetworkSecurityGroup nsg = networkManager.networkSecurityGroups()
                    .getById(resource.getNetworkSecurityGroupId());
            nicBuilder = nicBuilder.withExistingNetworkSecurityGroup(nsg);
        }

        if (resource.getTags() != null) {
            nicBuilder = nicBuilder.withTags(resource.getTags());
        }

        return nicBuilder.create();
    }

    private VirtualMachine createVirtualMachine(VirtualMachineResource resource, NetworkInterface nic) {
        // Determine if this is a Linux or Windows image
        boolean isLinux = isLinuxImage(resource.getImagePublisher(), resource.getImageOffer());

        if (isLinux) {
            return createLinuxVM(resource, nic);
        } else {
            return createWindowsVM(resource, nic);
        }
    }

    private boolean isLinuxImage(String publisher, String offer) {
        // Common Linux publishers
        List<String> linuxPublishers = List.of(
                "Canonical", "RedHat", "SUSE", "OpenLogic", "credativ",
                "CoreOS", "kinvolk", "Debian", "Oracle", "tunnelbiz"
        );
        if (publisher != null) {
            for (String p : linuxPublishers) {
                if (publisher.toLowerCase().contains(p.toLowerCase())) {
                    return true;
                }
            }
        }
        if (offer != null) {
            String offerLower = offer.toLowerCase();
            if (offerLower.contains("ubuntu") || offerLower.contains("centos") ||
                offerLower.contains("rhel") || offerLower.contains("debian") ||
                offerLower.contains("sles") || offerLower.contains("linux")) {
                return true;
            }
        }
        return false;
    }

    private VirtualMachine createLinuxVM(VirtualMachineResource resource, NetworkInterface nic) {
        var baseBuilder = computeManager.virtualMachines()
                .define(resource.getName())
                .withRegion(Region.fromName(resource.getLocation()))
                .withExistingResourceGroup(resource.getResourceGroup())
                .withExistingPrimaryNetworkInterface(nic)
                .withLatestLinuxImage(
                        resource.getImagePublisher(),
                        resource.getImageOffer(),
                        resource.getImageSku())
                .withRootUsername(resource.getAdminUsername());

        VirtualMachine.DefinitionStages.WithCreate createBuilder;

        // Set credentials - this transitions to WithCreate
        if (resource.getSshPublicKey() != null && !resource.getSshPublicKey().isBlank()) {
            createBuilder = baseBuilder.withSsh(resource.getSshPublicKey())
                    .withSize(VirtualMachineSizeTypes.fromString(resource.getSize()));
        } else {
            createBuilder = baseBuilder.withRootPassword(resource.getAdminPassword())
                    .withSize(VirtualMachineSizeTypes.fromString(resource.getSize()));
        }

        // Apply common configurations using WithCreate interface
        return applyCreateOptions(createBuilder, resource);
    }

    private VirtualMachine createWindowsVM(VirtualMachineResource resource, NetworkInterface nic) {
        var createBuilder = computeManager.virtualMachines()
                .define(resource.getName())
                .withRegion(Region.fromName(resource.getLocation()))
                .withExistingResourceGroup(resource.getResourceGroup())
                .withExistingPrimaryNetworkInterface(nic)
                .withLatestWindowsImage(
                        resource.getImagePublisher(),
                        resource.getImageOffer(),
                        resource.getImageSku())
                .withAdminUsername(resource.getAdminUsername())
                .withAdminPassword(resource.getAdminPassword())
                .withSize(VirtualMachineSizeTypes.fromString(resource.getSize()));

        // Apply common configurations
        return applyCreateOptions(createBuilder, resource);
    }

    private VirtualMachine applyCreateOptions(
            VirtualMachine.DefinitionStages.WithCreate builder,
            VirtualMachineResource resource) {

        // Set OS disk options
        if (resource.getOsDiskSizeGb() != null) {
            builder = builder.withOSDiskSizeInGB(resource.getOsDiskSizeGb());
        }

        if (resource.getOsDiskCaching() != null) {
            CachingTypes caching = CachingTypes.fromString(resource.getOsDiskCaching());
            builder = builder.withOSDiskCaching(caching);
        }

        // Set tags
        if (resource.getTags() != null) {
            builder = builder.withTags(resource.getTags());
        }

        return builder.create();
    }

    private com.azure.resourcemanager.network.models.Network getNetworkFromSubnet(String subnetId) {
        // Subnet ID format: /subscriptions/.../resourceGroups/.../providers/Microsoft.Network/virtualNetworks/{vnet}/subnets/{subnet}
        String[] parts = subnetId.split("/");
        String resourceGroup = null;
        String vnetName = null;

        for (int i = 0; i < parts.length; i++) {
            if ("resourceGroups".equals(parts[i]) && i + 1 < parts.length) {
                resourceGroup = parts[i + 1];
            }
            if ("virtualNetworks".equals(parts[i]) && i + 1 < parts.length) {
                vnetName = parts[i + 1];
            }
        }

        return networkManager.networks().getByResourceGroup(resourceGroup, vnetName);
    }

    private String getSubnetName(String subnetId) {
        // Get last part after /subnets/
        String[] parts = subnetId.split("/subnets/");
        return parts.length > 1 ? parts[1] : subnetId;
    }

    @Override
    public VirtualMachineResource read(VirtualMachineResource resource) {
        if (resource.getName() == null || resource.getResourceGroup() == null) {
            log.warn("Cannot read Virtual Machine without name and resourceGroup");
            return null;
        }

        log.info("Reading Virtual Machine '{}' in resource group '{}'",
                resource.getName(), resource.getResourceGroup());

        try {
            VirtualMachine vm = computeManager.virtualMachines()
                    .getByResourceGroup(resource.getResourceGroup(), resource.getName());

            if (vm == null) {
                return null;
            }

            NetworkInterface nic = vm.getPrimaryNetworkInterface();
            return mapToResource(vm, nic);

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("ResourceNotFound")) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public VirtualMachineResource update(VirtualMachineResource resource) {
        log.info("Updating Virtual Machine '{}' in resource group '{}'",
                resource.getName(), resource.getResourceGroup());

        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("Virtual Machine not found: " + resource.getName());
        }

        VirtualMachine vm = computeManager.virtualMachines()
                .getByResourceGroup(resource.getResourceGroup(), resource.getName());

        var update = vm.update();

        // Update size (may require restart)
        if (resource.getSize() != null && !resource.getSize().equals(current.getSize())) {
            log.info("Changing VM size from {} to {}",
                    current.getSize(), resource.getSize());
            update = update.withSize(VirtualMachineSizeTypes.fromString(resource.getSize()));
        }

        // Update tags
        if (resource.getTags() != null) {
            update = update.withTags(resource.getTags());
        }

        VirtualMachine updated = update.apply();
        return mapToResource(updated, updated.getPrimaryNetworkInterface());
    }

    @Override
    public boolean delete(VirtualMachineResource resource) {
        if (resource.getName() == null || resource.getResourceGroup() == null) {
            log.warn("Cannot delete Virtual Machine without name and resourceGroup");
            return false;
        }

        log.info("Deleting Virtual Machine '{}' from resource group '{}'",
                resource.getName(), resource.getResourceGroup());

        try {
            VirtualMachine vm = computeManager.virtualMachines()
                    .getByResourceGroup(resource.getResourceGroup(), resource.getName());

            if (vm == null) {
                return false;
            }

            // Get NIC and OS disk IDs before deletion
            String nicId = vm.primaryNetworkInterfaceId();
            String osDiskId = vm.osDiskId();

            // Delete VM
            computeManager.virtualMachines()
                    .deleteByResourceGroup(resource.getResourceGroup(), resource.getName());
            log.info("Deleted Virtual Machine: {}", resource.getName());

            // Delete NIC
            if (nicId != null) {
                networkManager.networkInterfaces().deleteById(nicId);
                log.info("Deleted network interface: {}", nicId);
            }

            // Delete OS disk
            if (osDiskId != null) {
                computeManager.disks().deleteById(osDiskId);
                log.info("Deleted OS disk: {}", osDiskId);
            }

            return true;

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("ResourceNotFound")) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public List<Diagnostic> validate(VirtualMachineResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getName() == null || resource.getName().isBlank()) {
            diagnostics.add(Diagnostic.error("name is required")
                    .withProperty("name"));
        } else if (resource.getName().length() > 64) {
            diagnostics.add(Diagnostic.error("name must be 64 characters or less")
                    .withProperty("name"));
        }

        if (resource.getResourceGroup() == null || resource.getResourceGroup().isBlank()) {
            diagnostics.add(Diagnostic.error("resourceGroup is required")
                    .withProperty("resourceGroup"));
        }

        if (resource.getLocation() == null || resource.getLocation().isBlank()) {
            diagnostics.add(Diagnostic.error("location is required")
                    .withProperty("location"));
        }

        if (resource.getSize() == null || resource.getSize().isBlank()) {
            diagnostics.add(Diagnostic.error("size is required")
                    .withProperty("size"));
        }

        if (resource.getAdminUsername() == null || resource.getAdminUsername().isBlank()) {
            diagnostics.add(Diagnostic.error("adminUsername is required")
                    .withProperty("adminUsername"));
        }

        if ((resource.getAdminPassword() == null || resource.getAdminPassword().isBlank()) &&
            (resource.getSshPublicKey() == null || resource.getSshPublicKey().isBlank())) {
            diagnostics.add(Diagnostic.error("either adminPassword or sshPublicKey is required")
                    .withProperty("adminPassword"));
        }

        if (resource.getImagePublisher() == null || resource.getImagePublisher().isBlank()) {
            diagnostics.add(Diagnostic.error("imagePublisher is required")
                    .withProperty("imagePublisher"));
        }

        if (resource.getImageOffer() == null || resource.getImageOffer().isBlank()) {
            diagnostics.add(Diagnostic.error("imageOffer is required")
                    .withProperty("imageOffer"));
        }

        if (resource.getImageSku() == null || resource.getImageSku().isBlank()) {
            diagnostics.add(Diagnostic.error("imageSku is required")
                    .withProperty("imageSku"));
        }

        if (resource.getSubnetId() == null || resource.getSubnetId().isBlank()) {
            diagnostics.add(Diagnostic.error("subnetId is required")
                    .withProperty("subnetId"));
        }

        // Validate OS disk type
        if (resource.getOsDiskType() != null && !resource.getOsDiskType().isBlank()) {
            List<String> validTypes = List.of("Standard_LRS", "StandardSSD_LRS",
                    "Premium_LRS", "UltraSSD_LRS");
            if (!validTypes.contains(resource.getOsDiskType())) {
                diagnostics.add(Diagnostic.error("osDiskType must be one of: " +
                        String.join(", ", validTypes))
                        .withProperty("osDiskType"));
            }
        }

        // Validate caching
        if (resource.getOsDiskCaching() != null && !resource.getOsDiskCaching().isBlank()) {
            List<String> validCaching = List.of("None", "ReadOnly", "ReadWrite");
            if (!validCaching.contains(resource.getOsDiskCaching())) {
                diagnostics.add(Diagnostic.error("osDiskCaching must be one of: " +
                        String.join(", ", validCaching))
                        .withProperty("osDiskCaching"));
            }
        }

        return diagnostics;
    }

    private VirtualMachineResource mapToResource(VirtualMachine vm, NetworkInterface nic) {
        var resource = new VirtualMachineResource();

        // Input properties
        resource.setName(vm.name());
        resource.setResourceGroup(vm.resourceGroupName());
        resource.setLocation(vm.regionName());
        resource.setSize(vm.size().toString());
        resource.setAdminUsername(vm.osProfile().adminUsername());
        resource.setComputerName(vm.computerName());

        // Image
        if (vm.storageProfile().imageReference() != null) {
            resource.setImagePublisher(vm.storageProfile().imageReference().publisher());
            resource.setImageOffer(vm.storageProfile().imageReference().offer());
            resource.setImageSku(vm.storageProfile().imageReference().sku());
            resource.setImageVersion(vm.storageProfile().imageReference().version());
        }

        // OS Disk
        if (vm.storageProfile().osDisk() != null) {
            resource.setOsDiskName(vm.storageProfile().osDisk().name());
            if (vm.storageProfile().osDisk().diskSizeGB() != null) {
                resource.setOsDiskSizeGb(vm.storageProfile().osDisk().diskSizeGB());
            }
            if (vm.storageProfile().osDisk().managedDisk() != null &&
                vm.storageProfile().osDisk().managedDisk().storageAccountType() != null) {
                resource.setOsDiskType(vm.storageProfile().osDisk().managedDisk()
                        .storageAccountType().toString());
            }
            if (vm.storageProfile().osDisk().caching() != null) {
                resource.setOsDiskCaching(vm.storageProfile().osDisk().caching().toString());
            }
        }

        // Network
        if (nic != null) {
            resource.setNetworkInterfaceId(nic.id());
            resource.setPrivateIp(nic.primaryPrivateIP());
            if (nic.primaryIPConfiguration() != null &&
                nic.primaryIPConfiguration().getPublicIpAddress() != null) {
                resource.setPublicIp(nic.primaryIPConfiguration().getPublicIpAddress().ipAddress());
                resource.setPublicIpAddressId(nic.primaryIPConfiguration().getPublicIpAddress().id());
            }
            if (nic.getNetworkSecurityGroup() != null) {
                resource.setNetworkSecurityGroupId(nic.getNetworkSecurityGroup().id());
            }
        }

        // Zones
        if (vm.availabilityZones() != null && !vm.availabilityZones().isEmpty()) {
            List<String> zones = new ArrayList<>();
            for (var zone : vm.availabilityZones()) {
                zones.add(zone.toString());
            }
            resource.setZones(zones);
        }

        // Tags
        if (vm.tags() != null && !vm.tags().isEmpty()) {
            resource.setTags(new HashMap<>(vm.tags()));
        }

        // Cloud-managed properties
        resource.setId(vm.id());
        resource.setVmId(vm.vmId());

        if (vm.innerModel().provisioningState() != null) {
            resource.setProvisioningState(vm.innerModel().provisioningState());
        }

        resource.setPowerState(vm.powerState() != null ? vm.powerState().toString() : null);

        return resource;
    }
}
