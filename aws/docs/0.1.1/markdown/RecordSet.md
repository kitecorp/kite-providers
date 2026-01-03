# RecordSet

## Example

```kite
resource RecordSet example {
    hostedZoneId     = "example-value"
    name             = "example-value"
    type             = "A"
    ttl              = 42
    records          = ["item1", "item2"]
    aliasTarget      = "..."
    weight           = 42
    setIdentifier    = "example-value"
    failover         = "PRIMARY"
    geoLocation      = "..."
    healthCheckId    = "example-value"
    multiValueAnswer = true
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `hostedZoneId` | `string` | — | — | No | The ID of the hosted zone |
| `name` | `string` | — | — | No | The name of the record (fully qualified domain name) |
| `type` | `string` | — | `A`, `AAAA`, `CNAME`, `MX`, `TXT`, `NS`, `SOA`, `SRV`, `CAA`, `PTR` | No | The DNS record type |
| `ttl` | `integer` | — | — | No | The TTL (time to live) in seconds |
| `records` | `list` | — | — | No | The record values |
| `aliasTarget` | `aliastarget` | — | — | No | Alias target for AWS resources (ELB, CloudFront, S3, etc.) |
| `weight` | `integer` | — | — | No | Weighted routing weight (0-255) |
| `setIdentifier` | `string` | — | — | No | Set identifier for routing policies |
| `failover` | `string` | — | `PRIMARY`, `SECONDARY` | No | Failover routing type |
| `geoLocation` | `geolocation` | — | — | No | Geolocation routing configuration |
| `healthCheckId` | `string` | — | — | No | Health check ID to associate with the record |
| `multiValueAnswer` | `boolean` | — | — | No | Whether this is a multivalue answer record |

[← Back to Index](README.md)
