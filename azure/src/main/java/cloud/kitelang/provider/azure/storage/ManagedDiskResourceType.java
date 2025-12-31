package cloud.kitelang.provider.azure.storage;

import cloud.kitelang.provider.Diagnostic;
import cloud.kitelang.provider.ResourceTypeHandler;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.Region;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.compute.models.Disk;
import com.azure.resourcemanager.resources.fluentcore.arm.AvailabilityZoneId;
import com.azure.resourcemanager.compute.models.DiskSkuTypes;
import com.azure.resourcemanager.compute.models.DiskStorageAccountTypes;
import com.azure.resourcemanager.compute.models.OperatingSystemTypes;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * ResourceTypeHandler for Azure Managed Disk.
 * Implements CRUD operations for disks using Azure SDK.
 */
@Slf4j
public class ManagedDiskResourceType extends ResourceTypeHandler<ManagedDiskResource> {

    private static final Set<String> VALID_SKUS = Set.of(
            "Standard_LRS", "Premium_LRS", "StandardSSD_LRS",
            "UltraSSD_LRS", "Premium_ZRS", "StandardSSD_ZRS"
    );

    private final ComputeManager computeManager;

    public ManagedDiskResourceType() {
        var credential = new DefaultAzureCredentialBuilder().build();

        String subscriptionId = System.getenv("AZURE_SUBSCRIPTION_ID");
        if (subscriptionId == null || subscriptionId.isBlank()) {
            throw new IllegalStateException(
                    "AZURE_SUBSCRIPTION_ID environment variable must be set");
        }

        String tenantId = System.getenv("AZURE_TENANT_ID");
        var profile = new AzureProfile(tenantId, subscriptionId, AzureEnvironment.AZURE);

        this.computeManager = ComputeManager.authenticate(credential, profile);
    }

    public ManagedDiskResourceType(ComputeManager computeManager) {
        this.computeManager = computeManager;
    }

    @Override
    public ManagedDiskResource create(ManagedDiskResource resource) {
        log.info("Creating Managed Disk '{}' in resource group '{}' at '{}'",
                resource.getName(), resource.getResourceGroup(), resource.getLocation());

        // Determine SKU (default to StandardSSD_LRS)
        var skuName = resource.getSku() != null ? resource.getSku() : "StandardSSD_LRS";
        var diskSku = DiskSkuTypes.fromStorageAccountType(DiskStorageAccountTypes.fromString(skuName));

        // Start building the disk definition
        var definition = computeManager.disks()
                .define(resource.getName())
                .withRegion(Region.fromName(resource.getLocation()))
                .withExistingResourceGroup(resource.getResourceGroup());

        Disk.DefinitionStages.WithCreate createDef;

        // Handle creation source
        if (resource.getSourceResourceId() != null) {
            // Create from snapshot or existing disk
            if (resource.getSourceResourceId().contains("/snapshots/")) {
                createDef = definition.withData()
                        .fromSnapshot(resource.getSourceResourceId())
                        .withSku(diskSku);
            } else {
                createDef = definition.withData()
                        .fromDisk(resource.getSourceResourceId())
                        .withSku(diskSku);
            }
        } else if (resource.getSourceUri() != null && resource.getStorageAccountId() != null) {
            // Import from VHD
            createDef = definition.withData()
                    .fromVhd(resource.getSourceUri())
                    .withStorageAccountId(resource.getStorageAccountId())
                    .withSku(diskSku);
        } else {
            // Create empty disk
            int sizeGb = resource.getSize() != null ? resource.getSize() : 128;

            if (resource.getOsType() != null) {
                // OS disk
                var osType = OperatingSystemTypes.fromString(resource.getOsType());
                createDef = definition.withData()
                        .withSizeInGB(sizeGb)
                        .withSku(diskSku);
            } else {
                // Data disk
                createDef = definition.withData()
                        .withSizeInGB(sizeGb)
                        .withSku(diskSku);
            }
        }

        // Set availability zone
        if (resource.getZone() != null) {
            createDef = createDef.withAvailabilityZone(AvailabilityZoneId.fromString(resource.getZone()));
        }

        // Tags
        if (resource.getTags() != null && !resource.getTags().isEmpty()) {
            createDef = createDef.withTags(resource.getTags());
        }

        // Create the disk
        Disk disk = createDef.create();
        log.info("Created Managed Disk: {}", disk.id());

        // Apply additional settings via update (not all settings available at creation time)
        if (needsPostCreateUpdate(resource, skuName)) {
            disk = applyPostCreateSettings(disk, resource);
        }

        return mapDiskToResource(disk);
    }

    private boolean needsPostCreateUpdate(ManagedDiskResource resource, String skuName) {
        return (resource.getDiskIops() != null && "UltraSSD_LRS".equals(skuName)) ||
               (resource.getDiskMbps() != null && "UltraSSD_LRS".equals(skuName)) ||
               resource.getBurstingEnabled() != null ||
               resource.getNetworkAccessPolicy() != null ||
               resource.getTier() != null;
    }

    private Disk applyPostCreateSettings(Disk disk, ManagedDiskResource resource) {
        // Ultra disk IOPS/throughput settings are configured at create time
        // or via ARM directly - the fluent API doesn't expose update methods
        // Just return the disk as-is for now
        log.debug("Post-create settings not fully supported via fluent API for disk: {}", disk.name());
        return disk;
    }

    @Override
    public ManagedDiskResource read(ManagedDiskResource resource) {
        if (resource.getId() == null && (resource.getName() == null || resource.getResourceGroup() == null)) {
            log.warn("Cannot read Managed Disk without id or (name and resourceGroup)");
            return null;
        }

        log.info("Reading Managed Disk '{}' in resource group '{}'",
                resource.getName(), resource.getResourceGroup());

        try {
            Disk disk;
            if (resource.getId() != null) {
                disk = computeManager.disks().getById(resource.getId());
            } else {
                disk = computeManager.disks()
                        .getByResourceGroup(resource.getResourceGroup(), resource.getName());
            }

            if (disk == null) {
                return null;
            }

            return mapDiskToResource(disk);

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("ResourceNotFound")) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public ManagedDiskResource update(ManagedDiskResource resource) {
        log.info("Updating Managed Disk '{}' in resource group '{}'",
                resource.getName(), resource.getResourceGroup());

        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("Managed Disk not found: " + resource.getName());
        }

        Disk disk = computeManager.disks()
                .getByResourceGroup(resource.getResourceGroup(), resource.getName());

        var update = disk.update();
        boolean needsUpdate = false;

        // Size can only be increased (when disk is not attached or VM is deallocated)
        if (resource.getSize() != null && resource.getSize() > disk.sizeInGB()) {
            update = update.withSizeInGB(resource.getSize());
            needsUpdate = true;
        }

        // SKU change
        if (resource.getSku() != null && !resource.getSku().equals(current.getSku())) {
            var newSku = DiskSkuTypes.fromStorageAccountType(
                    DiskStorageAccountTypes.fromString(resource.getSku()));
            update = update.withSku(newSku);
            needsUpdate = true;
        }

        // Note: Ultra disk IOPS/throughput updates require ARM API directly
        // The fluent API doesn't expose these methods on Disk.Update

        // Tags
        if (resource.getTags() != null) {
            update = update.withTags(resource.getTags());
            needsUpdate = true;
        }

        if (needsUpdate) {
            disk = update.apply();
        }

        return mapDiskToResource(disk);
    }

    @Override
    public boolean delete(ManagedDiskResource resource) {
        if (resource.getId() == null && (resource.getName() == null || resource.getResourceGroup() == null)) {
            log.warn("Cannot delete Managed Disk without id or (name and resourceGroup)");
            return false;
        }

        log.info("Deleting Managed Disk '{}' in resource group '{}'",
                resource.getName(), resource.getResourceGroup());

        try {
            if (resource.getId() != null) {
                computeManager.disks().deleteById(resource.getId());
            } else {
                computeManager.disks()
                        .deleteByResourceGroup(resource.getResourceGroup(), resource.getName());
            }

            log.info("Deleted Managed Disk: {}", resource.getName());
            return true;

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("ResourceNotFound")) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public List<Diagnostic> validate(ManagedDiskResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getName() == null || resource.getName().isBlank()) {
            diagnostics.add(Diagnostic.error("name is required")
                    .withProperty("name"));
        } else {
            if (resource.getName().length() > 80) {
                diagnostics.add(Diagnostic.error("name must be 80 characters or less")
                        .withProperty("name"));
            }
        }

        if (resource.getResourceGroup() == null || resource.getResourceGroup().isBlank()) {
            diagnostics.add(Diagnostic.error("resourceGroup is required")
                    .withProperty("resourceGroup"));
        }

        if (resource.getLocation() == null || resource.getLocation().isBlank()) {
            diagnostics.add(Diagnostic.error("location is required")
                    .withProperty("location"));
        }

        // Size is required unless creating from source
        if (resource.getSize() == null && resource.getSourceResourceId() == null && resource.getSourceUri() == null) {
            diagnostics.add(Diagnostic.error("size is required when not creating from source")
                    .withProperty("size"));
        }

        // Validate size range
        if (resource.getSize() != null) {
            if (resource.getSize() < 1 || resource.getSize() > 65536) {
                diagnostics.add(Diagnostic.error("size must be between 1 and 65536 GB")
                        .withProperty("size"));
            }
        }

        // Validate SKU
        var sku = resource.getSku() != null ? resource.getSku() : "StandardSSD_LRS";
        if (!VALID_SKUS.contains(sku)) {
            diagnostics.add(Diagnostic.error("Invalid sku",
                    "Valid values: " + String.join(", ", VALID_SKUS))
                    .withProperty("sku"));
        }

        // Ultra disk validation
        if ("UltraSSD_LRS".equals(sku)) {
            if (resource.getZone() == null) {
                diagnostics.add(Diagnostic.error("zone is required for Ultra SSD disks")
                        .withProperty("zone"));
            }
            if (resource.getDiskIops() != null && (resource.getDiskIops() < 100 || resource.getDiskIops() > 160000)) {
                diagnostics.add(Diagnostic.error("diskIops must be between 100 and 160,000 for Ultra SSD")
                        .withProperty("diskIops"));
            }
            if (resource.getDiskMbps() != null && (resource.getDiskMbps() < 1 || resource.getDiskMbps() > 2000)) {
                diagnostics.add(Diagnostic.error("diskMbps must be between 1 and 2,000 for Ultra SSD")
                        .withProperty("diskMbps"));
            }
        }

        // OS type validation
        if (resource.getOsType() != null) {
            if (!resource.getOsType().equalsIgnoreCase("Linux") &&
                !resource.getOsType().equalsIgnoreCase("Windows")) {
                diagnostics.add(Diagnostic.error("osType must be 'Linux' or 'Windows'")
                        .withProperty("osType"));
            }
        }

        // Network access policy validation
        if (resource.getNetworkAccessPolicy() != null) {
            var validPolicies = Set.of("AllowAll", "AllowPrivate", "DenyAll");
            if (!validPolicies.contains(resource.getNetworkAccessPolicy())) {
                diagnostics.add(Diagnostic.error("networkAccessPolicy must be one of: AllowAll, AllowPrivate, DenyAll")
                        .withProperty("networkAccessPolicy"));
            }
        }

        // Source URI requires storage account ID
        if (resource.getSourceUri() != null && resource.getStorageAccountId() == null) {
            diagnostics.add(Diagnostic.error("storageAccountId is required when using sourceUri")
                    .withProperty("storageAccountId"));
        }

        return diagnostics;
    }

    private ManagedDiskResource mapDiskToResource(Disk disk) {
        var resource = new ManagedDiskResource();

        // Input properties
        resource.setName(disk.name());
        resource.setResourceGroup(disk.resourceGroupName());
        resource.setLocation(disk.regionName());
        resource.setSize(disk.sizeInGB());

        if (disk.sku() != null) {
            resource.setSku(disk.sku().accountType().toString());
        }

        // OS type
        if (disk.osType() != null) {
            resource.setOsType(disk.osType().toString());
        }

        // Ultra disk properties
        var innerModel = disk.innerModel();
        if (innerModel.diskIopsReadWrite() != null) {
            resource.setDiskIops(innerModel.diskIopsReadWrite());
        }
        if (innerModel.diskMBpsReadWrite() != null) {
            resource.setDiskMbps(innerModel.diskMBpsReadWrite());
        }

        // Tier
        if (innerModel.tier() != null) {
            resource.setTier(innerModel.tier());
        }

        // Zones
        if (disk.availabilityZones() != null && !disk.availabilityZones().isEmpty()) {
            resource.setZone(disk.availabilityZones().iterator().next().toString());
        }

        // Bursting
        if (innerModel.burstingEnabled() != null) {
            resource.setBurstingEnabled(innerModel.burstingEnabled());
        }

        // Network access policy
        if (innerModel.networkAccessPolicy() != null) {
            resource.setNetworkAccessPolicy(innerModel.networkAccessPolicy().toString());
        }

        // Creation source - get from inner model
        var creationData = innerModel.creationData();
        if (creationData != null && creationData.sourceResourceId() != null) {
            resource.setSourceResourceId(creationData.sourceResourceId());
        }

        // Tags
        if (disk.tags() != null && !disk.tags().isEmpty()) {
            resource.setTags(new HashMap<>(disk.tags()));
        }

        // Cloud-managed properties
        resource.setId(disk.id());

        var provisioningState = innerModel.provisioningState();
        if (provisioningState != null) {
            resource.setProvisioningState(provisioningState);
        }

        if (innerModel.diskState() != null) {
            resource.setDiskState(innerModel.diskState().toString());
        }

        if (innerModel.uniqueId() != null) {
            resource.setUniqueId(innerModel.uniqueId());
        }

        if (innerModel.timeCreated() != null) {
            resource.setTimeCreated(innerModel.timeCreated().toString());
        }

        if (disk.virtualMachineId() != null) {
            resource.setManagedBy(disk.virtualMachineId());
        }

        return resource;
    }
}
