# Subnet

## Example

```kite
resource Subnet example {
    vpcId                       = "example-value"
    cidrBlock                   = "example-value"
    ipv6CidrBlock               = "example-value"
    availabilityZone            = "example-value"
    mapPublicIpOnLaunch         = false
    assignIpv6AddressOnCreation = false
    tags                        = { key = "value" }
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `vpcId` | `string` | — | — | No | The VPC ID where the subnet will be created |
| `cidrBlock` | `string` | — | — | No | The IPv4 CIDR block for the subnet (e.g., 10.0.1.0/24) |
| `ipv6CidrBlock` | `string` | — | — | No | The IPv6 CIDR block for the subnet |
| `availabilityZone` | `string` | — | — | No | The Availability Zone for the subnet (e.g., us-east-1a) |
| `mapPublicIpOnLaunch` | `boolean` | `false` | — | No | Auto-assign public IPv4 addresses to instances |
| `assignIpv6AddressOnCreation` | `boolean` | `false` | — | No | Assign IPv6 addresses to instances |
| `tags` | `map` | — | — | No | Tags to apply to the subnet |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `subnetId` | `string` | The subnet ID assigned by AWS |
| `state` | `string` | The current state of the subnet (pending, available) |
| `availabilityZoneId` | `string` | The Availability Zone ID |
| `availableIpAddressCount` | `integer` | The number of available IPv4 addresses |
| `defaultForAz` | `boolean` | Whether this is the default subnet for the AZ |
| `ownerId` | `string` | The AWS account ID that owns the subnet |
| `arn` | `string` | *📥 importable* The Amazon Resource Name (ARN) of the subnet |

[← Back to Index](README.md)
