# PublicIpAddress

## Example

```kite
resource PublicIpAddress example {
    name                 = "example-value"
    resourceGroup        = "example-value"
    location             = "example-value"
    sku                  = "Basic"
    allocationMethod     = "Dynamic"
    ipVersion            = "IPv4"
    idleTimeoutInMinutes = 4
    domainNameLabel      = "example-value"
    zones                = ["item1", "item2"]
    tags                 = { key = "value" }
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `name` | `string` | — | — | No | The name of the public IP address |
| `resourceGroup` | `string` | — | — | No | The Azure resource group name |
| `location` | `string` | — | — | No | The Azure region/location |
| `sku` | `string` | `Basic` | `Basic`, `Standard` | No | The SKU. Standard is required for zone redundancy and NAT Gateway |
| `allocationMethod` | `string` | `Dynamic` | `Static`, `Dynamic` | No | The allocation method. Standard SKU requires Static |
| `ipVersion` | `string` | `IPv4` | `IPv4`, `IPv6` | No | The IP version |
| `idleTimeoutInMinutes` | `integer` | `4` | — | No | The idle timeout in minutes. Range: 4-30 |
| `domainNameLabel` | `string` | — | — | No | The domain name label for DNS. Creates: {label}.{region}.cloudapp.azure.com |
| `zones` | `list` | — | — | No | The availability zones for the IP. Only for Standard SKU |
| `tags` | `map` | — | — | No | Tags to apply to the public IP address |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `id` | `string` | — | — | No | *☁️ cloud-managed, 📥 importable* The Azure resource ID of the public IP |
| `ipAddress` | `string` | — | — | No | *☁️ cloud-managed* The assigned IP address |
| `provisioningState` | `string` | — | — | No | *☁️ cloud-managed* The provisioning state of the public IP |
| `resourceGuid` | `string` | — | — | No | *☁️ cloud-managed* The resource GUID |
| `fqdn` | `string` | — | — | No | *☁️ cloud-managed* The FQDN of the DNS record |

[← Back to Index](README.md)
