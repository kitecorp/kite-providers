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

    /**
     * The name of the managed disk.
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
     * The Azure region/location (e.g., "eastus", "westeurope").
     * Required.
     */
    @Property
    private String location;

    /**
     * The size of the disk in GB.
     * Required unless sourceResourceId is specified.
     */
    @Property
    private Integer size;

    /**
     * The disk SKU (storage type).
     * Valid values: Standard_LRS, Premium_LRS, StandardSSD_LRS, UltraSSD_LRS, Premium_ZRS, StandardSSD_ZRS
     * Default: StandardSSD_LRS
     */
    @Property
    private String sku;

    /**
     * The disk tier for Premium and Ultra disks.
     * Controls the baseline IOPS and throughput of the disk.
     * Example: P30, P40, P50 for Premium_LRS
     */
    @Property
    private String tier;

    /**
     * The IOPS read/write budget for Ultra disks.
     * Range: 100 - 160,000 IOPS
     */
    @Property
    private Long diskIops;

    /**
     * The throughput in MB/s for Ultra disks.
     * Range: 1 - 2,000 MB/s
     */
    @Property
    private Long diskMbps;

    /**
     * The operating system type if this is an OS disk.
     * Valid values: Linux, Windows
     */
    @Property
    private String osType;

    /**
     * The source resource ID to create the disk from.
     * Can be a snapshot, image, or another disk.
     */
    @Property
    private String sourceResourceId;

    /**
     * The source URI for import/upload scenarios.
     * Used for importing a VHD from a storage account.
     */
    @Property
    private String sourceUri;

    /**
     * The storage account ID where sourceUri is located.
     */
    @Property
    private String storageAccountId;

    /**
     * The availability zone for the disk.
     * Example: "1", "2", "3"
     */
    @Property
    private String zone;

    /**
     * Whether to enable bursting for Premium SSD disks.
     * Only supported for Premium_LRS disks larger than 512 GB.
     */
    @Property
    private Boolean burstingEnabled;

    /**
     * Enable network access policy for the disk.
     * Valid values: AllowAll, AllowPrivate, DenyAll
     */
    @Property
    private String networkAccessPolicy;

    /**
     * The disk access ID for private endpoint connections.
     */
    @Property
    private String diskAccessId;

    /**
     * Tags to apply to the disk.
     */
    @Property
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    /**
     * The Azure resource ID of the disk.
     */
    @Cloud
    @Property
    private String id;

    /**
     * The provisioning state of the disk.
     */
    @Cloud
    @Property
    private String provisioningState;

    /**
     * The disk state.
     * Values: Unattached, Attached, Reserved, ActiveSAS, ReadyToUpload, ActiveUpload
     */
    @Cloud
    @Property
    private String diskState;

    /**
     * The unique identifier for the disk.
     */
    @Cloud
    @Property
    private String uniqueId;

    /**
     * The time the disk was created.
     */
    @Cloud
    @Property
    private String timeCreated;

    /**
     * The ID of the VM this disk is attached to (if any).
     */
    @Cloud
    @Property
    private String managedBy;
}
