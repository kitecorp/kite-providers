package cloud.kitelang.provider.azure;

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

    /**
     * The name of the blob container.
     * Must be 3-63 characters, lowercase letters, numbers, and hyphens.
     * Required.
     */
    @Property
    private String name;

    /**
     * The name of the storage account.
     * Required.
     */
    @Property
    private String storageAccountName;

    /**
     * The resource group name.
     * Required.
     */
    @Property
    private String resourceGroup;

    /**
     * The level of public access to the container.
     * Valid values: None, Blob, Container
     * - None: No public access (default)
     * - Blob: Public read access for blobs only
     * - Container: Public read access for container and blobs
     */
    @Property
    private String publicAccess;

    /**
     * Container metadata as key-value pairs.
     */
    @Property
    private Map<String, String> metadata;

    /**
     * Enable immutable storage with versioning.
     * Once enabled, cannot be disabled.
     */
    @Property
    private Boolean immutableStorageWithVersioning;

    /**
     * Default encryption scope for the container.
     */
    @Property
    private String defaultEncryptionScope;

    /**
     * Deny encryption scope override.
     * When true, blobs must use the container's default encryption scope.
     */
    @Property
    private Boolean denyEncryptionScopeOverride;

    // --- Cloud-managed properties (read-only) ---

    /**
     * The resource ID of the container.
     */
    @Cloud
    @Property
    private String id;

    /**
     * The ETag of the container.
     */
    @Cloud
    @Property
    private String etag;

    /**
     * The last modified time.
     */
    @Cloud
    @Property
    private String lastModifiedTime;

    /**
     * The lease status.
     */
    @Cloud
    @Property
    private String leaseStatus;

    /**
     * The lease state.
     */
    @Cloud
    @Property
    private String leaseState;

    /**
     * Whether the container has immutability policy.
     */
    @Cloud
    @Property
    private Boolean hasImmutabilityPolicy;

    /**
     * Whether the container has legal hold.
     */
    @Cloud
    @Property
    private Boolean hasLegalHold;
}
