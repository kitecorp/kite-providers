# ResourceGroup

## Example

```kite
resource ResourceGroup example {
    name     = "example-value"
    location = "example-value"
    tags     = { key = "value" }
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `name` | `string` | — | — | No | The name of the resource group. Must be unique within the subscription |
| `location` | `string` | — | — | No | The Azure region/location (e.g., eastus, westeurope) |
| `tags` | `map` | — | — | No | Tags to apply to the resource group |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `id` | `string` | — | — | No | *☁️ cloud-managed, 📥 importable* The Azure resource ID of the resource group |
| `provisioningState` | `string` | — | — | No | *☁️ cloud-managed* The provisioning state of the resource group |

[← Back to Index](README.md)
