# Listener

## Example

```kite
resource Listener example {
    loadBalancerArn       = "example-value"
    port                  = 42
    protocol              = "HTTP"
    certificateArn        = "example-value"
    sslPolicy             = "example-value"
    alpnPolicy            = "example-value"
    defaultTargetGroupArn = "example-value"
    defaultActionType     = "forward"
    redirectConfig        = "..."
    fixedResponseConfig   = "..."
    tags                  = { key = "value" }
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `loadBalancerArn` | `string` | — | — | No | The ARN of the load balancer |
| `port` | `integer` | — | — | No | The port on which the listener listens |
| `protocol` | `string` | — | `HTTP`, `HTTPS`, `TCP`, `TLS`, `UDP`, `TCP_UDP`, `GENEVE` | No | The protocol for the listener |
| `certificateArn` | `string` | — | — | No | The ARN of the default SSL server certificate (HTTPS/TLS only) |
| `sslPolicy` | `string` | — | — | No | The SSL policy for HTTPS/TLS listeners |
| `alpnPolicy` | `string` | — | — | No | The ALPN policy for TLS listeners (NLB only) |
| `defaultTargetGroupArn` | `string` | — | — | No | The ARN of the default target group for forward action |
| `defaultActionType` | `string` | `forward` | `forward`, `redirect`, `fixed-response` | No | The default action type |
| `redirectConfig` | `redirectconfig` | — | — | No | Redirect configuration when defaultActionType is 'redirect' |
| `fixedResponseConfig` | `fixedresponseconfig` | — | — | No | Fixed response configuration when defaultActionType is 'fixed-response' |
| `tags` | `map` | — | — | No | Tags to apply to the listener |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `arn` | `string` | *📥 importable* The ARN of the listener |

[← Back to Index](README.md)
