# RouteTable

## Example

```kite
resource RouteTable example {
    name                       = "example-value"
    resourceGroup              = "example-value"
    location                   = "example-value"
    disableBgpRoutePropagation = false
    routes                     = ["item1", "item2"]
    tags                       = { key = "value" }
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `name` | `string` | — | — | No | The name of the route table |
| `resourceGroup` | `string` | — | — | No | The Azure resource group name |
| `location` | `string` | — | — | No | The Azure region/location |
| `disableBgpRoutePropagation` | `boolean` | `false` | — | No | Whether to disable BGP route propagation |
| `routes` | `list` | — | — | No | Routes in this route table |
| `tags` | `map` | — | — | No | Tags to apply to the route table |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `id` | `string` | *📥 importable* The Azure resource ID of the route table |
| `provisioningState` | `string` | The provisioning state of the route table |
| `associatedSubnetIds` | `list` | List of subnet IDs associated with this route table |

[← Back to Index](README.md)
