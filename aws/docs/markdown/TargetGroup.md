# TargetGroup

## Example

```kite
resource TargetGroup example {
    name                = "example-value"
    port                = 42
    protocol            = "HTTP"
    protocolVersion     = "HTTP1"
    vpcId               = "example-value"
    targetType          = "instance"
    ipAddressType       = "ipv4"
    healthCheck         = "..."
    deregistrationDelay = 300
    slowStart           = 0
    stickiness          = "..."
    targets             = ["item1", "item2"]
    tags                = { key = "value" }
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `name` | `string` | — | — | No | The name of the target group |
| `port` | `integer` | — | — | No | The port on which targets receive traffic |
| `protocol` | `string` | `HTTP` | `HTTP`, `HTTPS`, `TCP`, `TLS`, `UDP`, `TCP_UDP`, `GENEVE` | No | The protocol for routing traffic |
| `protocolVersion` | `string` | `HTTP1` | `HTTP1`, `HTTP2`, `GRPC` | No | The protocol version (ALB only) |
| `vpcId` | `string` | — | — | No | The VPC ID for the target group |
| `targetType` | `string` | `instance` | `instance`, `ip`, `lambda`, `alb` | No | The target type |
| `ipAddressType` | `string` | `ipv4` | `ipv4`, `ipv6` | No | The IP address type |
| `healthCheck` | `healthcheck` | — | — | No | Health check configuration |
| `deregistrationDelay` | `integer` | `300` | — | No | Deregistration delay in seconds |
| `slowStart` | `integer` | `0` | — | No | Slow start duration in seconds (ALB only) |
| `stickiness` | `stickiness` | — | — | No | Stickiness configuration |
| `targets` | `list` | — | — | No | Target IDs to register (instance IDs or IP addresses) |
| `tags` | `map` | — | — | No | Tags to apply to the target group |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `arn` | `string` | — | — | No | *☁️ cloud-managed, 📥 importable* The Amazon Resource Name (ARN) of the target group |
| `loadBalancerArns` | `list` | — | — | No | *☁️ cloud-managed* The load balancer ARNs attached to this target group |

[← Back to Index](README.md)
