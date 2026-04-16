# ResourceRecordSet

## Example

```kite
resource ResourceRecordSet example {
    managedZone = "example-value"
    name        = "example-value"
    type        = "A"
    ttl         = 3.14
    rrdatas     = "..."
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `managedZone` | `string` | — | — | No | The managed zone name this record belongs to |
| `name` | `string` | — | — | No | The fully qualified domain name (must end with a dot) |
| `type` | `string` | — | `A`, `AAAA`, `CNAME`, `MX`, `TXT`, `NS`, `SOA`, `SRV`, `CAA`, `PTR`, `SPF` | No | The DNS record type |
| `ttl` | `number` | — | — | No | The TTL (time to live) in seconds |
| `rrdatas` | `any[]` | — | — | No | The record data values |

[← Back to Index](README.md)
