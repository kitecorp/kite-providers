# VpcNetwork

## Example

```kite
resource VpcNetwork example {
    name                  = "example-value"
    autoCreateSubnetworks = true
    routingMode           = "REGIONAL"
    description           = "example-value"
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `name` | `string` | — | — | No | The name of the network |
| `autoCreateSubnetworks` | `boolean` | `true` | — | No | Whether to automatically create subnetworks in each region |
| `routingMode` | `string` | `REGIONAL` | `REGIONAL`, `GLOBAL` | No | The network-wide routing mode |
| `description` | `string` | — | — | No | An optional description of the network |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `networkId` | `string` | *📥 importable* The unique network ID assigned by GCP |
| `selfLink` | `string` | The self-link URL of the network |
| `creationTimestamp` | `string` | The creation timestamp of the network |

[← Back to Index](README.md)
