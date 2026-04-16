# TargetGroup

## Example

```kite
resource TargetGroup example {
    name                = "example-value"
    port                = 3.14
    protocol            = "HTTP"
    protocolVersion     = "HTTP1"
    vpcId               = "example-value"
    targetType          = "instance"
    ipAddressType       = "ipv4"
    healthCheck         = "..."
    deregistrationDelay = 300
    slowStart           = 0
    stickiness          = "..."
    targets             = "..."
    tags                = "..."
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `name` | `string` | — | — | No | The name of the target group |
| `port` | `number` | — | — | No | The port on which targets receive traffic |
| `protocol` | `string` | `HTTP` | `HTTP`, `HTTPS`, `TCP`, `TLS`, `UDP`, `TCP_UDP`, `GENEVE` | No | The protocol for routing traffic |
| `protocolVersion` | `string` | `HTTP1` | `HTTP1`, `HTTP2`, `GRPC` | No | The protocol version (ALB only) |
| `vpcId` | `string` | — | — | No | The VPC ID for the target group |
| `targetType` | `string` | `instance` | `instance`, `ip`, `lambda`, `alb` | No | The target type |
| `ipAddressType` | `string` | `ipv4` | `ipv4`, `ipv6` | No | The IP address type |
| `healthCheck` | `any` | — | — | No | Health check configuration |
| `deregistrationDelay` | `number` | `300` | — | No | Deregistration delay in seconds |
| `slowStart` | `number` | `0` | — | No | Slow start duration in seconds (ALB only) |
| `stickiness` | `any` | — | — | No | Stickiness configuration |
| `targets` | `any[]` | — | — | No | Target IDs to register (instance IDs or IP addresses) |
| `tags` | `object` | — | — | No | Tags to apply to the target group |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `arn` | `string` | *📥 importable* The Amazon Resource Name (ARN) of the target group |
| `loadBalancerArns` | `any[]` | The load balancer ARNs attached to this target group |

[← Back to Index](README.md)
