# LoadBalancer

## Example

```kite
resource LoadBalancer example {
    name                     = "example-value"
    resourceGroup            = "example-value"
    location                 = "example-value"
    sku                      = "Standard"
    tier                     = "Regional"
    frontendIpConfigurations = ["item1", "item2"]
    backendAddressPools      = ["item1", "item2"]
    healthProbes             = ["item1", "item2"]
    loadBalancingRules       = ["item1", "item2"]
    inboundNatRules          = ["item1", "item2"]
    outboundRules            = ["item1", "item2"]
    tags                     = { key = "value" }
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `name` | `string` | — | — | No | The name of the load balancer |
| `resourceGroup` | `string` | — | — | No | The resource group name |
| `location` | `string` | — | — | No | The Azure region |
| `sku` | `string` | `Standard` | `Basic`, `Standard` | No | The SKU of the load balancer |
| `tier` | `string` | `Regional` | `Regional`, `Global` | No | The SKU tier |
| `frontendIpConfigurations` | `list` | — | — | No | Frontend IP configurations. At least one is required |
| `backendAddressPools` | `list` | — | — | No | Backend address pools |
| `healthProbes` | `list` | — | — | No | Health probes for backend health monitoring |
| `loadBalancingRules` | `list` | — | — | No | Load balancing rules |
| `inboundNatRules` | `list` | — | — | No | Inbound NAT rules for port forwarding |
| `outboundRules` | `list` | — | — | No | Outbound rules (Standard SKU only) |
| `tags` | `map` | — | — | No | Tags to apply to the load balancer |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `id` | `string` | *📥 importable* The resource ID of the load balancer |
| `provisioningState` | `string` | Provisioning state of the load balancer |

[← Back to Index](README.md)
