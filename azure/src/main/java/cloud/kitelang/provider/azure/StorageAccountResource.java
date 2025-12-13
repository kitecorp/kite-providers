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
 * Azure Storage Account resource.
 *
 * Example usage:
 * <pre>
 * resource StorageAccount main {
 *     name = "mystorageaccount"
 *     resourceGroup = rg.name
 *     location = "eastus"
 *     sku = "Standard_LRS"
 *     kind = "StorageV2"
 *     accessTier = "Hot"
 *     enableHttpsTrafficOnly = true
 *     tags = {
 *         Environment: "production"
 *     }
 * }
 *
 * resource StorageAccount premium {
 *     name = "mypremiumstorage"
 *     resourceGroup = rg.name
 *     location = "eastus"
 *     sku = "Premium_LRS"
 *     kind = "BlockBlobStorage"
 *     enableHierarchicalNamespace = true
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("StorageAccount")
public class StorageAccountResource {

    /**
     * The name of the storage account.
     * Must be 3-24 characters, lowercase letters and numbers only.
     * Must be globally unique.
     * Required.
     */
    @Property
    private String name;

    /**
     * The resource group name.
     * Required.
     */
    @Property
    private String resourceGroup;

    /**
     * The Azure region for the storage account.
     * Required.
     */
    @Property
    private String location;

    /**
     * The SKU (pricing tier and replication).
     * Valid values: Standard_LRS, Standard_GRS, Standard_RAGRS, Standard_ZRS,
     *               Premium_LRS, Premium_ZRS
     * Default: Standard_LRS
     */
    @Property
    private String sku;

    /**
     * The kind of storage account.
     * Valid values: Storage, StorageV2, BlobStorage, BlockBlobStorage, FileStorage
     * Default: StorageV2
     */
    @Property
    private String kind;

    /**
     * The access tier for blob storage.
     * Valid values: Hot, Cool
     * Default: Hot
     */
    @Property
    private String accessTier;

    /**
     * Enable HTTPS traffic only.
     * Default: true
     */
    @Property
    private Boolean enableHttpsTrafficOnly;

    /**
     * Minimum TLS version.
     * Valid values: TLS1_0, TLS1_1, TLS1_2
     * Default: TLS1_2
     */
    @Property
    private String minimumTlsVersion;

    /**
     * Allow blob public access.
     * Default: false
     */
    @Property
    private Boolean allowBlobPublicAccess;

    /**
     * Allow shared key access.
     * Default: true
     */
    @Property
    private Boolean allowSharedKeyAccess;

    /**
     * Enable hierarchical namespace (Data Lake Storage Gen2).
     */
    @Property
    private Boolean enableHierarchicalNamespace;

    /**
     * Enable infrastructure encryption.
     */
    @Property
    private Boolean infrastructureEncryptionEnabled;

    /**
     * Enable large file shares (100 TiB capacity).
     */
    @Property
    private Boolean largeFileSharesEnabled;

    /**
     * Network rules for firewall configuration.
     */
    @Property
    private NetworkRules networkRules;

    /**
     * Tags to apply to the storage account.
     */
    @Property
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    /**
     * The resource ID of the storage account.
     */
    @Cloud
    @Property
    private String id;

    /**
     * The primary blob endpoint.
     */
    @Cloud
    @Property
    private String primaryBlobEndpoint;

    /**
     * The primary file endpoint.
     */
    @Cloud
    @Property
    private String primaryFileEndpoint;

    /**
     * The primary queue endpoint.
     */
    @Cloud
    @Property
    private String primaryQueueEndpoint;

    /**
     * The primary table endpoint.
     */
    @Cloud
    @Property
    private String primaryTableEndpoint;

    /**
     * The primary access key.
     */
    @Cloud
    @Property
    private String primaryAccessKey;

    /**
     * The primary connection string.
     */
    @Cloud
    @Property
    private String primaryConnectionString;

    /**
     * The provisioning state.
     */
    @Cloud
    @Property
    private String provisioningState;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkRules {
        /**
         * Default action: Allow or Deny.
         */
        private String defaultAction;

        /**
         * IP rules - list of allowed IP addresses or CIDR ranges.
         */
        private java.util.List<String> ipRules;

        /**
         * Virtual network subnet IDs that are allowed.
         */
        private java.util.List<String> virtualNetworkSubnetIds;

        /**
         * Bypass options: AzureServices, Metrics, Logging, None.
         */
        private java.util.List<String> bypass;
    }
}
