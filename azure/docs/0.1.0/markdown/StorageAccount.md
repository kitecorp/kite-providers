# StorageAccount

## Example

```kite
resource StorageAccount example {
    name                            = "example-value"
    resourceGroup                   = "example-value"
    location                        = "example-value"
    sku                             = "Standard_LRS"
    kind                            = "StorageV2"
    accessTier                      = "Hot"
    enableHttpsTrafficOnly          = true
    minimumTlsVersion               = "TLS1_2"
    allowBlobPublicAccess           = false
    allowSharedKeyAccess            = true
    enableHierarchicalNamespace     = false
    infrastructureEncryptionEnabled = false
    largeFileSharesEnabled          = false
    networkRules                    = "..."
    tags                            = { key = "value" }
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `name` | `string` | — | — | No | The name of the storage account (3-24 chars, lowercase letters and numbers, globally unique) |
| `resourceGroup` | `string` | — | — | No | The resource group name |
| `location` | `string` | — | — | No | The Azure region for the storage account |
| `sku` | `string` | `Standard_LRS` | `Standard_LRS`, `Standard_GRS`, `Standard_RAGRS`, `Standard_ZRS`, `Premium_LRS`, `Premium_ZRS` | No | The SKU (pricing tier and replication) |
| `kind` | `string` | `StorageV2` | `Storage`, `StorageV2`, `BlobStorage`, `BlockBlobStorage`, `FileStorage` | No | The kind of storage account |
| `accessTier` | `string` | `Hot` | `Hot`, `Cool` | No | The access tier for blob storage |
| `enableHttpsTrafficOnly` | `boolean` | `true` | — | No | Enable HTTPS traffic only |
| `minimumTlsVersion` | `string` | `TLS1_2` | `TLS1_0`, `TLS1_1`, `TLS1_2` | No | Minimum TLS version |
| `allowBlobPublicAccess` | `boolean` | `false` | — | No | Allow blob public access |
| `allowSharedKeyAccess` | `boolean` | `true` | — | No | Allow shared key access |
| `enableHierarchicalNamespace` | `boolean` | `false` | — | No | Enable hierarchical namespace (Data Lake Storage Gen2) |
| `infrastructureEncryptionEnabled` | `boolean` | `false` | — | No | Enable infrastructure encryption |
| `largeFileSharesEnabled` | `boolean` | `false` | — | No | Enable large file shares (100 TiB capacity) |
| `networkRules` | `networkrules` | — | — | No | Network rules for firewall configuration |
| `tags` | `map` | — | — | No | Tags to apply to the storage account |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `id` | `string` | *📥 importable* The resource ID of the storage account |
| `primaryBlobEndpoint` | `string` | The primary blob endpoint |
| `primaryFileEndpoint` | `string` | The primary file endpoint |
| `primaryQueueEndpoint` | `string` | The primary queue endpoint |
| `primaryTableEndpoint` | `string` | The primary table endpoint |
| `primaryAccessKey` | `string` | The primary access key |
| `primaryConnectionString` | `string` | The primary connection string |
| `provisioningState` | `string` | The provisioning state |

[← Back to Index](README.md)
