# ElasticIp

## Example

```kite
resource ElasticIp example {
    domain             = "vpc"
    networkBorderGroup = "example-value"
    publicIpv4Pool     = "example-value"
    tags               = { key = "value" }
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `domain` | `string` | `vpc` | `vpc`, `standard` | No | The domain for the Elastic IP |
| `networkBorderGroup` | `string` | — | — | No | The ID of the network border group for Local/Wavelength Zones |
| `publicIpv4Pool` | `string` | — | — | No | The ID of an address pool for BYOIP |
| `tags` | `map` | — | — | No | Tags to apply to the Elastic IP |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `allocationId` | `string` | — | — | No | *☁️ cloud-managed* The allocation ID for the Elastic IP |
| `publicIp` | `string` | — | — | No | *☁️ cloud-managed* The Elastic IP address |
| `instanceId` | `string` | — | — | No | *☁️ cloud-managed* The ID of the associated instance (if any) |
| `associationId` | `string` | — | — | No | *☁️ cloud-managed* The association ID (if associated) |
| `networkInterfaceId` | `string` | — | — | No | *☁️ cloud-managed* The ID of the network interface (if associated) |
| `privateIpAddress` | `string` | — | — | No | *☁️ cloud-managed* The private IP address associated with the Elastic IP |

[← Back to Index](README.md)
