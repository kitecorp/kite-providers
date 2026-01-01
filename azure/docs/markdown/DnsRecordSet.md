# DnsRecordSet

## Example

```kite
resource DnsRecordSet example {
    zoneName         = "example-value"
    resourceGroup    = "example-value"
    name             = "example-value"
    type             = "A"
    ttl              = 42
    aRecords         = ["item1", "item2"]
    aaaaRecords      = ["item1", "item2"]
    cnameRecord      = "example-value"
    mxRecords        = ["item1", "item2"]
    txtRecords       = ["item1", "item2"]
    nsRecords        = ["item1", "item2"]
    srvRecords       = ["item1", "item2"]
    caaRecords       = ["item1", "item2"]
    ptrRecords       = ["item1", "item2"]
    targetResourceId = "example-value"
    metadata         = { key = "value" }
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `zoneName` | `string` | — | — | No | The name of the DNS zone |
| `resourceGroup` | `string` | — | — | No | The resource group name |
| `name` | `string` | — | — | No | The name of the record set. Use @ for the zone apex |
| `type` | `string` | — | `A`, `AAAA`, `CNAME`, `MX`, `TXT`, `NS`, `SRV`, `CAA`, `PTR`, `SOA` | No | The DNS record type |
| `ttl` | `integer` | — | — | No | The TTL (time to live) in seconds. Required unless targetResourceId is specified |
| `aRecords` | `list` | — | — | No | A records (IPv4 addresses). Used when type = A |
| `aaaaRecords` | `list` | — | — | No | AAAA records (IPv6 addresses). Used when type = AAAA |
| `cnameRecord` | `string` | — | — | No | CNAME record (canonical name). Used when type = CNAME |
| `mxRecords` | `list` | — | — | No | MX records. Used when type = MX |
| `txtRecords` | `list` | — | — | No | TXT records. Used when type = TXT |
| `nsRecords` | `list` | — | — | No | NS records. Used when type = NS |
| `srvRecords` | `list` | — | — | No | SRV records. Used when type = SRV |
| `caaRecords` | `list` | — | — | No | CAA records. Used when type = CAA |
| `ptrRecords` | `list` | — | — | No | PTR records. Used when type = PTR |
| `targetResourceId` | `string` | — | — | No | Target resource ID for alias record. Points to an Azure resource (e.g., Public IP, Traffic Manager) |
| `metadata` | `map` | — | — | No | Metadata for the record set |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `id` | `string` | — | — | No | *☁️ cloud-managed, 📥 importable* The resource ID of the record set |
| `fqdn` | `string` | — | — | No | *☁️ cloud-managed* The fully qualified domain name of the record set |
| `provisioningState` | `string` | — | — | No | *☁️ cloud-managed* The provisioning state |
| `etag` | `string` | — | — | No | *☁️ cloud-managed* The ETag of the record set |

[← Back to Index](README.md)
