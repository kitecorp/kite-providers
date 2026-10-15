# ForwardingRule

## Example

```kite
resource ForwardingRule example {
    name                = "example-value"
    target              = "example-value"
    portRange           = "example-value"
    ipProtocol          = "TCP"
    loadBalancingScheme = "EXTERNAL"
    network             = "example-value"
    subnetwork          = "example-value"
    region              = "example-value"
    labels              = "..."
    description         = "example-value"
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `name` | `string` | — | — | No | The name of the forwarding rule |
| `target` | `string` | — | — | No | The target resource URL (target pool, proxy, or backend service) |
| `portRange` | `string` | — | — | No | The port range for this forwarding rule (e.g., '80', '8080-8090') |
| `ipProtocol` | `string` | `TCP` | `TCP`, `UDP`, `ESP`, `AH`, `SCTP`, `ICMP` | No | The IP protocol for this forwarding rule |
| `loadBalancingScheme` | `string` | `EXTERNAL` | `EXTERNAL`, `EXTERNAL_MANAGED`, `INTERNAL`, `INTERNAL_MANAGED`, `INTERNAL_SELF_MANAGED` | No | The load balancing scheme |
| `network` | `string` | — | — | No | The VPC network for internal load balancers |
| `subnetwork` | `string` | — | — | No | The subnetwork for internal load balancers |
| `region` | `string` | — | — | No | The GCP region for this forwarding rule (regional rules only) |
| `labels` | `object` | — | — | No | Labels to apply to the forwarding rule |
| `description` | `string` | — | — | No | An optional description |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `forwardingRuleId` | `string` | *📥 importable* The unique ID of the forwarding rule |
| `selfLink` | `string` | The self-link URL of the forwarding rule |
| `ipAddress` | `string` | The IP address assigned to this forwarding rule |

[← Back to Index](README.md)
