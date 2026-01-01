# Vpc

## Example

```kite
resource Vpc example {
    cidrBlock          = "example-value"
    ipv6CidrBlock      = "example-value"
    instanceTenancy    = "default"
    enableDnsSupport   = true
    enableDnsHostnames = false
    tags               = { key = "value" }
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `cidrBlock` | `string` | — | — | No | The IPv4 CIDR block for the VPC (e.g., 10.0.0.0/16) |
| `ipv6CidrBlock` | `string` | — | — | No | The IPv6 CIDR block for the VPC. If specified, AWS associates an IPv6 CIDR block |
| `instanceTenancy` | `string` | `default` | `default`, `dedicated` | No | Tenancy option for instances |
| `enableDnsSupport` | `boolean` | `true` | — | No | Enable DNS support in the VPC |
| `enableDnsHostnames` | `boolean` | `false` | — | No | Enable DNS hostnames in the VPC |
| `tags` | `map` | — | — | No | Tags to apply to the VPC |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `vpcId` | `string` | The VPC ID assigned by AWS |
| `state` | `string` | The current state of the VPC (pending, available) |
| `mainRouteTableId` | `string` | The ID of the main route table |
| `defaultNetworkAclId` | `string` | The ID of the default network ACL |
| `defaultSecurityGroupId` | `string` | The ID of the default security group |
| `ownerId` | `string` | The AWS account ID that owns the VPC |
| `arn` | `string` | *📥 importable* The Amazon Resource Name (ARN) of the VPC |

[← Back to Index](README.md)
