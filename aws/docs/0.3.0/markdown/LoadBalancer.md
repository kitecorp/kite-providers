# LoadBalancer

## Example

```kite
resource LoadBalancer example {
    name                   = "example-value"
    type                   = "application"
    scheme                 = "internet-facing"
    subnets                = "..."
    securityGroups         = "..."
    ipAddressType          = "ipv4"
    deletionProtection     = false
    crossZoneLoadBalancing = true
    enableHttp2            = true
    idleTimeout            = 60
    accessLogsEnabled      = false
    accessLogsBucket       = "example-value"
    accessLogsPrefix       = "example-value"
    tags                   = "..."
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `name` | `string` | — | — | No | The name of the load balancer. Must be unique within your AWS account |
| `type` | `string` | `application` | `application`, `network`, `gateway` | No | The type of load balancer |
| `scheme` | `string` | `internet-facing` | `internet-facing`, `internal` | No | The scheme for the load balancer |
| `subnets` | `any[]` | — | — | No | The subnet IDs. At least two in different Availability Zones |
| `securityGroups` | `any[]` | — | — | No | The security group IDs. Required for ALB, not applicable for NLB |
| `ipAddressType` | `string` | `ipv4` | `ipv4`, `dualstack` | No | The IP address type |
| `deletionProtection` | `boolean` | `false` | — | No | Enable deletion protection |
| `crossZoneLoadBalancing` | `boolean` | — | — | No | Enable cross-zone load balancing |
| `enableHttp2` | `boolean` | `true` | — | No | Enable HTTP/2 support (ALB only) |
| `idleTimeout` | `number` | `60` | — | No | Idle timeout in seconds (ALB only) |
| `accessLogsEnabled` | `boolean` | `false` | — | No | Enable access logs |
| `accessLogsBucket` | `string` | — | — | No | S3 bucket for access logs |
| `accessLogsPrefix` | `string` | — | — | No | S3 bucket prefix for access logs |
| `tags` | `object` | — | — | No | Tags to apply to the load balancer |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `arn` | `string` | *📥 importable* The Amazon Resource Name (ARN) of the load balancer |
| `dnsName` | `string` | The DNS name of the load balancer |
| `canonicalHostedZoneId` | `string` | The canonical hosted zone ID of the load balancer |
| `vpcId` | `string` | The VPC ID of the load balancer |
| `state` | `string` | The current state of the load balancer |
| `availabilityZones` | `any[]` | The Availability Zones for the load balancer |

[← Back to Index](README.md)
