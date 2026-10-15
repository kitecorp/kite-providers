# DnsRecordSet

## Example

```kite
resource DnsRecordSet example {
    zoneName         = "example-value"
    resourceGroup    = "example-value"
    name             = "example-value"
    type             = "A"
    ttl              = 3.14
    aRecords         = "..."
    aaaaRecords      = "..."
    cnameRecord      = "example-value"
    mxRecords        = "..."
    txtRecords       = "..."
    nsRecords        = "..."
    srvRecords       = "..."
    caaRecords       = "..."
    ptrRecords       = "..."
    targetResourceId = "example-value"
    metadata         = "..."
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `zoneName` | `string` | — | — | No | The name of the DNS zone |
| `resourceGroup` | `string` | — | — | No | The resource group name |
| `name` | `string` | — | — | No | The name of the record set. Use @ for the zone apex |
| `type` | `string` | — | `A`, `AAAA`, `CNAME`, `MX`, `TXT`, `NS`, `SRV`, `CAA`, `PTR`, `SOA` | No | The DNS record type |
| `ttl` | `number` | — | — | No | The TTL (time to live) in seconds. Required unless targetResourceId is specified |
| `aRecords` | `any[]` | — | — | No | A records (IPv4 addresses). Used when type = A |
| `aaaaRecords` | `any[]` | — | — | No | AAAA records (IPv6 addresses). Used when type = AAAA |
| `cnameRecord` | `string` | — | — | No | CNAME record (canonical name). Used when type = CNAME |
| `mxRecords` | `any[]` | — | — | No | MX records. Used when type = MX |
| `txtRecords` | `any[]` | — | — | No | TXT records. Used when type = TXT |
| `nsRecords` | `any[]` | — | — | No | NS records. Used when type = NS |
| `srvRecords` | `any[]` | — | — | No | SRV records. Used when type = SRV |
| `caaRecords` | `any[]` | — | — | No | CAA records. Used when type = CAA |
| `ptrRecords` | `any[]` | — | — | No | PTR records. Used when type = PTR |
| `targetResourceId` | `string` | — | — | No | Target resource ID for alias record. Points to an Azure resource (e.g., Public IP, Traffic Manager) |
| `metadata` | `object` | — | — | No | Metadata for the record set |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `id` | `string` | *📥 importable* The resource ID of the record set |
| `fqdn` | `string` | The fully qualified domain name of the record set |
| `provisioningState` | `string` | The provisioning state |
| `etag` | `string` | The ETag of the record set |

[← Back to Index](README.md)
