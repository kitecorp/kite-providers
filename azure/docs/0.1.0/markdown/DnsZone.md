# DnsZone

## Example

```kite
resource DnsZone example {
    name                          = "example-value"
    resourceGroup                 = "example-value"
    zoneType                      = "Public"
    registrationVirtualNetworkIds = ["item1", "item2"]
    resolutionVirtualNetworkIds   = ["item1", "item2"]
    tags                          = { key = "value" }
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `name` | `string` | — | — | No | The domain name for the DNS zone. Must be a fully qualified domain name (e.g., example.com) |
| `resourceGroup` | `string` | — | — | No | The resource group name |
| `zoneType` | `string` | `Public` | `Public`, `Private` | No | The type of DNS zone |
| `registrationVirtualNetworkIds` | `list` | — | — | No | Virtual network IDs for auto-registration (private zones only). VMs in these networks will have their DNS records auto-registered |
| `resolutionVirtualNetworkIds` | `list` | — | — | No | Virtual network IDs for resolution (private zones only). VMs in these networks can resolve records in this zone |
| `tags` | `map` | — | — | No | Tags to apply to the DNS zone |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `id` | `string` | *📥 importable* The resource ID of the DNS zone |
| `nameServers` | `list` | The name servers for the DNS zone |
| `numberOfRecordSets` | `integer` | The number of record sets in the DNS zone |
| `maxNumberOfRecordSets` | `integer` | The maximum number of record sets allowed |
| `etag` | `string` | The ETag of the DNS zone |

[← Back to Index](README.md)
