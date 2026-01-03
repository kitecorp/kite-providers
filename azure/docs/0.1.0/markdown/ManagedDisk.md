# ManagedDisk

## Example

```kite
resource ManagedDisk example {
    name                = "example-value"
    resourceGroup       = "example-value"
    location            = "example-value"
    size                = 42
    sku                 = "StandardSSD_LRS"
    tier                = "example-value"
    diskIops            = 42
    diskMbps            = 42
    osType              = "Linux"
    sourceResourceId    = "example-value"
    sourceUri           = "example-value"
    storageAccountId    = "example-value"
    zone                = "example-value"
    burstingEnabled     = false
    networkAccessPolicy = "AllowAll"
    diskAccessId        = "example-value"
    tags                = { key = "value" }
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `name` | `string` | — | — | No | The name of the managed disk |
| `resourceGroup` | `string` | — | — | No | The Azure resource group name |
| `location` | `string` | — | — | No | The Azure region/location (e.g., eastus, westeurope) |
| `size` | `integer` | — | — | No | The size of the disk in GB |
| `sku` | `string` | `StandardSSD_LRS` | `Standard_LRS`, `Premium_LRS`, `StandardSSD_LRS`, `UltraSSD_LRS`, `Premium_ZRS`, `StandardSSD_ZRS` | No | The disk SKU (storage type) |
| `tier` | `string` | — | — | No | The disk tier for Premium and Ultra disks (e.g., P30, P40, P50) |
| `diskIops` | `integer` | — | — | No | The IOPS read/write budget for Ultra disks (100 - 160,000) |
| `diskMbps` | `integer` | — | — | No | The throughput in MB/s for Ultra disks (1 - 2,000) |
| `osType` | `string` | — | `Linux`, `Windows` | No | The operating system type if this is an OS disk |
| `sourceResourceId` | `string` | — | — | No | The source resource ID to create the disk from (snapshot, image, or disk) |
| `sourceUri` | `string` | — | — | No | The source URI for importing a VHD from a storage account |
| `storageAccountId` | `string` | — | — | No | The storage account ID where sourceUri is located |
| `zone` | `string` | — | — | No | The availability zone for the disk (1, 2, or 3) |
| `burstingEnabled` | `boolean` | `false` | — | No | Enable bursting for Premium SSD disks > 512 GB |
| `networkAccessPolicy` | `string` | `AllowAll` | `AllowAll`, `AllowPrivate`, `DenyAll` | No | Network access policy for the disk |
| `diskAccessId` | `string` | — | — | No | The disk access ID for private endpoint connections |
| `tags` | `map` | — | — | No | Tags to apply to the disk |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `id` | `string` | *📥 importable* The Azure resource ID of the disk |
| `provisioningState` | `string` | The provisioning state of the disk |
| `diskState` | `string` | The disk state (Unattached, Attached, Reserved, etc.) |
| `uniqueId` | `string` | The unique identifier for the disk |
| `timeCreated` | `string` | The time the disk was created |
| `managedBy` | `string` | The ID of the VM this disk is attached to |

[← Back to Index](README.md)
