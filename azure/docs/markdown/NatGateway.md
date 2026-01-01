# NatGateway

## Example

```kite
resource NatGateway example {
    name                 = "example-value"
    resourceGroup        = "example-value"
    location             = "example-value"
    idleTimeoutInMinutes = 4
    publicIpAddressIds   = ["item1", "item2"]
    publicIpPrefixIds    = ["item1", "item2"]
    skuName              = "Standard"
    tags                 = { key = "value" }
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `name` | `string` | — | — | No | The name of the NAT gateway |
| `resourceGroup` | `string` | — | — | No | The Azure resource group name |
| `location` | `string` | — | — | No | The Azure region/location |
| `idleTimeoutInMinutes` | `integer` | `4` | — | No | The idle timeout in minutes. Range: 4-120 |
| `publicIpAddressIds` | `list` | — | — | No | List of public IP address resource IDs to associate |
| `publicIpPrefixIds` | `list` | — | — | No | List of public IP prefix resource IDs to associate |
| `skuName` | `string` | `Standard` | `Standard` | No | The SKU name |
| `tags` | `map` | — | — | No | Tags to apply to the NAT gateway |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `id` | `string` | *📥 importable* The Azure resource ID of the NAT gateway |
| `provisioningState` | `string` | The provisioning state of the NAT gateway |
| `resourceGuid` | `string` | The resource GUID of the NAT gateway |
| `subnetIds` | `list` | List of subnet IDs using this NAT gateway |

[← Back to Index](README.md)
