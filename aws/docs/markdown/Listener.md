# Listener

## Example

```kite
resource Listener example {
    loadBalancerArn       = "example-value"
    port                  = 42
    protocol              = "example-value"
    certificateArn        = "example-value"
    sslPolicy             = "example-value"
    alpnPolicy            = "example-value"
    defaultTargetGroupArn = "example-value"
    defaultActionType     = "example-value"
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
| `protocol` | `string` | — | — | No | The protocol: HTTP, HTTPS, TCP, TLS, UDP, TCP_UDP, GENEVE |
| `certificateArn` | `string` | — | — | No | The ARN of the default SSL server certificate (HTTPS/TLS only) |
| `sslPolicy` | `string` | — | — | No | The SSL policy for HTTPS/TLS listeners |
| `alpnPolicy` | `string` | — | — | No | The ALPN policy for TLS listeners (NLB only) |
| `defaultTargetGroupArn` | `string` | — | — | No | The ARN of the default target group for forward action |
| `defaultActionType` | `string` | — | — | No | The default action type: forward, redirect, fixed-response |
| `redirectConfig` | `redirectconfig` | — | — | No | Redirect configuration when defaultActionType is 'redirect' |
| `fixedResponseConfig` | `fixedresponseconfig` | — | — | No | Fixed response configuration when defaultActionType is 'fixed-response' |
| `tags` | `map` | — | — | No | Tags to apply to the listener |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `arn` | `string` | — | — | No | *☁️ cloud-managed* The ARN of the listener |

[← Back to Index](README.md)
