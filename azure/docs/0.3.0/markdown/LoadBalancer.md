# LoadBalancer

## Example

```kite
resource LoadBalancer example {
    name                     = "example-value"
    resourceGroup            = "example-value"
    location                 = "example-value"
    sku                      = "Standard"
    tier                     = "Regional"
    frontendIpConfigurations = "..."
    backendAddressPools      = "..."
    healthProbes             = "..."
    loadBalancingRules       = "..."
    inboundNatRules          = "..."
    outboundRules            = "..."
    tags                     = "..."
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
| `frontendIpConfigurations` | `any[]` | — | — | No | Frontend IP configurations. At least one is required |
| `backendAddressPools` | `any[]` | — | — | No | Backend address pools |
| `healthProbes` | `any[]` | — | — | No | Health probes for backend health monitoring |
| `loadBalancingRules` | `any[]` | — | — | No | Load balancing rules |
| `inboundNatRules` | `any[]` | — | — | No | Inbound NAT rules for port forwarding |
| `outboundRules` | `any[]` | — | — | No | Outbound rules (Standard SKU only) |
| `tags` | `object` | — | — | No | Tags to apply to the load balancer |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `id` | `string` | *📥 importable* The resource ID of the load balancer |
| `provisioningState` | `string` | Provisioning state of the load balancer |

[← Back to Index](README.md)
