# VirtualMachine

## Example

```kite
resource VirtualMachine example {
    name                   = "example-value"
    resourceGroup          = "example-value"
    location               = "example-value"
    size                   = "example-value"
    adminUsername          = "example-value"
    adminPassword          = "example-value"
    sshPublicKey           = "example-value"
    imagePublisher         = "example-value"
    imageOffer             = "example-value"
    imageSku               = "example-value"
    imageVersion           = "latest"
    subnetId               = "example-value"
    publicIpAddressId      = "example-value"
    networkSecurityGroupId = "example-value"
    osDiskSizeGb           = 42
    osDiskType             = "Premium_LRS"
    osDiskCaching          = "ReadWrite"
    computerName           = "example-value"
    customData             = "example-value"
    zones                  = ["item1", "item2"]
    tags                   = { key = "value" }
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `name` | `string` | — | — | No | The name of the virtual machine |
| `resourceGroup` | `string` | — | — | No | The Azure resource group name |
| `location` | `string` | — | — | No | The Azure region/location |
| `size` | `string` | — | — | No | The VM size (e.g., Standard_B2s, Standard_D2s_v3) |
| `adminUsername` | `string` | — | — | No | The admin username for the VM |
| `adminPassword` | `string` | — | — | No | The admin password (for Windows or password auth) |
| `sshPublicKey` | `string` | — | — | No | The SSH public key for Linux VMs |
| `imagePublisher` | `string` | — | — | No | The image publisher (e.g., Canonical, MicrosoftWindowsServer) |
| `imageOffer` | `string` | — | — | No | The image offer (e.g., 0001-com-ubuntu-server-jammy, WindowsServer) |
| `imageSku` | `string` | — | — | No | The image SKU (e.g., 22_04-lts-gen2, 2022-datacenter-azure-edition) |
| `imageVersion` | `string` | `latest` | — | No | The image version |
| `subnetId` | `string` | — | — | No | The subnet ID to attach the VM to |
| `publicIpAddressId` | `string` | — | — | No | The public IP address ID to associate |
| `networkSecurityGroupId` | `string` | — | — | No | The network security group ID for the NIC |
| `osDiskSizeGb` | `integer` | — | — | No | The OS disk size in GB |
| `osDiskType` | `string` | `Premium_LRS` | `Standard_LRS`, `StandardSSD_LRS`, `Premium_LRS`, `UltraSSD_LRS` | No | The OS disk type |
| `osDiskCaching` | `string` | `ReadWrite` | `None`, `ReadOnly`, `ReadWrite` | No | The OS disk caching |
| `computerName` | `string` | — | — | No | The computer name (hostname). Defaults to VM name |
| `customData` | `string` | — | — | No | Custom data for cloud-init (Linux) or CustomScriptExtension (Windows) |
| `zones` | `list` | — | — | No | Availability zones for the VM |
| `tags` | `map` | — | — | No | Tags to apply to the VM and its resources |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `id` | `string` | *📥 importable* The Azure resource ID of the VM |
| `vmId` | `string` | The VM unique ID |
| `provisioningState` | `string` | The provisioning state |
| `powerState` | `string` | The power state (running, deallocated, etc.) |
| `privateIp` | `string` | The private IP address |
| `publicIp` | `string` | The public IP address (if assigned) |
| `networkInterfaceId` | `string` | The network interface ID |
| `osDiskName` | `string` | The OS disk name |

[← Back to Index](README.md)
