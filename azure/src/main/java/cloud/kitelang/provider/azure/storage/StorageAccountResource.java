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

    @Property(description = "The name of the storage account (3-24 chars, lowercase letters and numbers, globally unique)", optional = false)
    private String name;

    @Property(description = "The resource group name", optional = false)
    private String resourceGroup;

    @Property(description = "The Azure region for the storage account", optional = false)
    private String location;

    @Property(description = "The SKU (pricing tier and replication)",
              validValues = {"Standard_LRS", "Standard_GRS", "Standard_RAGRS", "Standard_ZRS", "Premium_LRS", "Premium_ZRS"})
    private String sku = "Standard_LRS";

    @Property(description = "The kind of storage account",
              validValues = {"Storage", "StorageV2", "BlobStorage", "BlockBlobStorage", "FileStorage"})
    private String kind = "StorageV2";

    @Property(description = "The access tier for blob storage",
              validValues = {"Hot", "Cool"})
    private String accessTier = "Hot";

    @Property(description = "Enable HTTPS traffic only")
    private Boolean enableHttpsTrafficOnly = true;

    @Property(description = "Minimum TLS version",
              validValues = {"TLS1_0", "TLS1_1", "TLS1_2"})
    private String minimumTlsVersion = "TLS1_2";

    @Property(description = "Allow blob public access")
    private Boolean allowBlobPublicAccess = false;

    @Property(description = "Allow shared key access")
    private Boolean allowSharedKeyAccess = true;

    @Property(description = "Enable hierarchical namespace (Data Lake Storage Gen2)")
    private Boolean enableHierarchicalNamespace = false;

    @Property(description = "Enable infrastructure encryption")
    private Boolean infrastructureEncryptionEnabled = false;

    @Property(description = "Enable large file shares (100 TiB capacity)")
    private Boolean largeFileSharesEnabled = false;

    @Property(description = "Network rules for firewall configuration")
    private NetworkRules networkRules;

    @Property(description = "Tags to apply to the storage account")
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    @Cloud
    @Property(description = "The resource ID of the storage account")
    private String id;

    @Cloud
    @Property(description = "The primary blob endpoint")
    private String primaryBlobEndpoint;

    @Cloud
    @Property(description = "The primary file endpoint")
    private String primaryFileEndpoint;

    @Cloud
    @Property(description = "The primary queue endpoint")
    private String primaryQueueEndpoint;

    @Cloud
    @Property(description = "The primary table endpoint")
    private String primaryTableEndpoint;

    @Cloud
    @Property(description = "The primary access key")
    private String primaryAccessKey;

    @Cloud
    @Property(description = "The primary connection string")
    private String primaryConnectionString;

    @Cloud
    @Property(description = "The provisioning state")
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
