# NetworkSecurityGroup

## Example

```kite
resource NetworkSecurityGroup example {
    name          = "example-value"
    resourceGroup = "example-value"
    location      = "example-value"
    securityRules = ["item1", "item2"]
    tags          = { key = "value" }
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `name` | `string` | — | — | No | The name of the network security group |
| `resourceGroup` | `string` | — | — | No | The Azure resource group name |
| `location` | `string` | — | — | No | The Azure region/location |
| `securityRules` | `list` | — | — | No | List of security rules. Each rule defines allowed or denied traffic |
| `tags` | `map` | — | — | No | Tags to apply to the NSG |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `id` | `string` | *📥 importable* The Azure resource ID |
| `provisioningState` | `string` | The provisioning state |
| `resourceGuid` | `string` | The resource GUID |
| `networkInterfaceIds` | `list` | List of network interfaces associated with this NSG |
| `subnetIds` | `list` | List of subnets associated with this NSG |

[← Back to Index](README.md)
