package cloud.kitelang.provider.azure.storage;

import cloud.kitelang.api.annotations.Cloud;
import cloud.kitelang.api.annotations.Property;
import cloud.kitelang.api.annotations.TypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Azure Managed Disk resource.
 * Equivalent to AWS EBS Volume.
 *
 * Example usage:
 * <pre>
 * resource ManagedDisk data {
 *     name = "data-disk"
 *     resourceGroup = "my-resource-group"
 *     location = "eastus"
 *     size = 128
 *     sku = "Premium_LRS"
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
@TypeName("ManagedDisk")
public class ManagedDiskResource {

    @Property(description = "The name of the managed disk", optional = false)
    private String name;

    @Property(description = "The Azure resource group name", optional = false)
    private String resourceGroup;

    @Property(description = "The Azure region/location (e.g., eastus, westeurope)", optional = false)
    private String location;

    @Property(description = "The size of the disk in GB")
    private Integer size;

    @Property(description = "The disk SKU (storage type)",
              validValues = {"Standard_LRS", "Premium_LRS", "StandardSSD_LRS", "UltraSSD_LRS", "Premium_ZRS", "StandardSSD_ZRS"})
    private String sku = "StandardSSD_LRS";

    @Property(description = "The disk tier for Premium and Ultra disks (e.g., P30, P40, P50)")
    private String tier;

    @Property(description = "The IOPS read/write budget for Ultra disks (100 - 160,000)")
    private Long diskIops;

    @Property(description = "The throughput in MB/s for Ultra disks (1 - 2,000)")
    private Long diskMbps;

    @Property(description = "The operating system type if this is an OS disk",
              validValues = {"Linux", "Windows"})
    private String osType;

    @Property(description = "The source resource ID to create the disk from (snapshot, image, or disk)")
    private String sourceResourceId;

    @Property(description = "The source URI for importing a VHD from a storage account")
    private String sourceUri;

    @Property(description = "The storage account ID where sourceUri is located")
    private String storageAccountId;

    @Property(description = "The availability zone for the disk (1, 2, or 3)")
    private String zone;

    @Property(description = "Enable bursting for Premium SSD disks > 512 GB")
    private Boolean burstingEnabled = false;

    @Property(description = "Network access policy for the disk",
              validValues = {"AllowAll", "AllowPrivate", "DenyAll"})
    private String networkAccessPolicy = "AllowAll";

    @Property(description = "The disk access ID for private endpoint connections")
    private String diskAccessId;

    @Property(description = "Tags to apply to the disk")
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    @Cloud
    @Property(description = "The Azure resource ID of the disk")
    private String id;

    @Cloud
    @Property(description = "The provisioning state of the disk")
    private String provisioningState;

    @Cloud
    @Property(description = "The disk state (Unattached, Attached, Reserved, etc.)")
    private String diskState;

    @Cloud
    @Property(description = "The unique identifier for the disk")
    private String uniqueId;

    @Cloud
    @Property(description = "The time the disk was created")
    private String timeCreated;

    @Cloud
    @Property(description = "The ID of the VM this disk is attached to")
    private String managedBy;
}
