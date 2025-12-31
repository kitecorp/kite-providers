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
 * Azure Blob Container resource.
 *
 * Example usage:
 * <pre>
 * resource BlobContainer data {
 *     name = "mydata"
 *     storageAccountName = storage.name
 *     resourceGroup = rg.name
 *     publicAccess = "None"
 *     metadata = {
 *         purpose: "application-data"
 *     }
 * }
 *
 * resource BlobContainer public {
 *     name = "public-assets"
 *     storageAccountName = storage.name
 *     resourceGroup = rg.name
 *     publicAccess = "Blob"
 * }
 *
 * resource BlobContainer immutable {
 *     name = "audit-logs"
 *     storageAccountName = storage.name
 *     resourceGroup = rg.name
 *     immutableStorageWithVersioning = true
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("BlobContainer")
public class BlobContainerResource {

    @Property(description = "The name of the blob container. Must be 3-63 characters, lowercase letters, numbers, and hyphens", optional = false)
    private String name;

    @Property(description = "The name of the storage account", optional = false)
    private String storageAccountName;

    @Property(description = "The resource group name", optional = false)
    private String resourceGroup;

    @Property(description = "The level of public access to the container. None: No public access, Blob: Public read access for blobs only, Container: Public read access for container and blobs",
              validValues = {"None", "Blob", "Container"})
    private String publicAccess = "None";

    @Property(description = "Container metadata as key-value pairs")
    private Map<String, String> metadata;

    @Property(description = "Enable immutable storage with versioning. Once enabled, cannot be disabled")
    private Boolean immutableStorageWithVersioning;

    @Property(description = "Default encryption scope for the container")
    private String defaultEncryptionScope;

    @Property(description = "Deny encryption scope override. When true, blobs must use the container's default encryption scope")
    private Boolean denyEncryptionScopeOverride;

    // --- Cloud-managed properties (read-only) ---

    @Cloud(importable = true)
    @Property(description = "The resource ID of the container")
    private String id;

    @Cloud
    @Property(description = "The ETag of the container")
    private String etag;

    @Cloud
    @Property(description = "The last modified time")
    private String lastModifiedTime;

    @Cloud
    @Property(description = "The lease status")
    private String leaseStatus;

    @Cloud
    @Property(description = "The lease state")
    private String leaseState;

    @Cloud
    @Property(description = "Whether the container has immutability policy")
    private Boolean hasImmutabilityPolicy;

    @Cloud
    @Property(description = "Whether the container has legal hold")
    private Boolean hasLegalHold;
}
