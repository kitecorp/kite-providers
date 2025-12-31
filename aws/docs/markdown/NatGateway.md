# NatGateway

## Example

```kite
resource NatGateway example {
    subnetId         = "example-value"
    allocationId     = "example-value"
    connectivityType = "example-value"
    tags             = { key = "value" }
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `subnetId` | `string` | — | — | No | The subnet ID where the NAT gateway will be created |
| `allocationId` | `string` | — | — | No | The allocation ID of the Elastic IP for public NAT gateway |
| `connectivityType` | `string` | — | — | No | The connectivity type: 'public' or 'private'. Default: 'public' |
| `tags` | `map` | — | — | No | Tags to apply to the NAT gateway |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `natGatewayId` | `string` | — | — | No | *☁️ cloud-managed* The NAT gateway ID assigned by AWS |
| `state` | `string` | — | — | No | *☁️ cloud-managed* The current state of the NAT gateway |
| `vpcId` | `string` | — | — | No | *☁️ cloud-managed* The VPC ID that contains the NAT gateway |
| `publicIp` | `string` | — | — | No | *☁️ cloud-managed* The public IP address associated with the NAT gateway |
| `privateIp` | `string` | — | — | No | *☁️ cloud-managed* The private IP address of the NAT gateway |

[← Back to Index](README.md)
