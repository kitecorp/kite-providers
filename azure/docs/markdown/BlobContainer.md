# BlobContainer

## Example

```kite
resource BlobContainer example {
    name                           = "example-value"
    storageAccountName             = "example-value"
    resourceGroup                  = "example-value"
    publicAccess                   = "None"
    metadata                       = { key = "value" }
    immutableStorageWithVersioning = true
    defaultEncryptionScope         = "example-value"
    denyEncryptionScopeOverride    = true
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `name` | `string` | — | — | No | The name of the blob container. Must be 3-63 characters, lowercase letters, numbers, and hyphens |
| `storageAccountName` | `string` | — | — | No | The name of the storage account |
| `resourceGroup` | `string` | — | — | No | The resource group name |
| `publicAccess` | `string` | `None` | `None`, `Blob`, `Container` | No | The level of public access to the container. None: No public access, Blob: Public read access for blobs only, Container: Public read access for container and blobs |
| `metadata` | `map` | — | — | No | Container metadata as key-value pairs |
| `immutableStorageWithVersioning` | `boolean` | — | — | No | Enable immutable storage with versioning. Once enabled, cannot be disabled |
| `defaultEncryptionScope` | `string` | — | — | No | Default encryption scope for the container |
| `denyEncryptionScopeOverride` | `boolean` | — | — | No | Deny encryption scope override. When true, blobs must use the container's default encryption scope |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `id` | `string` | *📥 importable* The resource ID of the container |
| `etag` | `string` | The ETag of the container |
| `lastModifiedTime` | `string` | The last modified time |
| `leaseStatus` | `string` | The lease status |
| `leaseState` | `string` | The lease state |
| `hasImmutabilityPolicy` | `boolean` | Whether the container has immutability policy |
| `hasLegalHold` | `boolean` | Whether the container has legal hold |

[← Back to Index](README.md)
