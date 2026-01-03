# Vnet

## Example

```kite
resource Vnet example {
    name                 = "example-value"
    resourceGroup        = "example-value"
    location             = "example-value"
    addressSpaces        = ["item1", "item2"]
    dnsServers           = ["item1", "item2"]
    enableDdosProtection = false
    enableVmProtection   = false
    tags                 = { key = "value" }
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `name` | `string` | — | — | No | The name of the virtual network |
| `resourceGroup` | `string` | — | — | No | The Azure resource group name |
| `location` | `string` | — | — | No | The Azure region/location (e.g., eastus, westeurope) |
| `addressSpaces` | `list` | — | — | No | The address spaces for the VNet in CIDR notation |
| `dnsServers` | `list` | — | — | No | Custom DNS servers for the VNet. If not specified, Azure-provided DNS is used |
| `enableDdosProtection` | `boolean` | `false` | — | No | Enable DDoS protection for the VNet |
| `enableVmProtection` | `boolean` | `false` | — | No | Enable VM protection for the VNet |
| `tags` | `map` | — | — | No | Tags to apply to the VNet |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `id` | `string` | *📥 importable* The Azure resource ID of the VNet |
| `provisioningState` | `string` | The provisioning state of the VNet |
| `resourceGuid` | `string` | The resource GUID of the VNet |
| `hasSubnets` | `boolean` | Whether the VNet has any subnets |

[← Back to Index](README.md)
