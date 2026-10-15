# RecordSet

## Example

```kite
resource RecordSet example {
    hostedZoneId     = "example-value"
    name             = "example-value"
    type             = "A"
    ttl              = 3.14
    records          = "..."
    aliasTarget      = "..."
    weight           = 3.14
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
| `ttl` | `number` | — | — | No | The TTL (time to live) in seconds |
| `records` | `any[]` | — | — | No | The record values |
| `aliasTarget` | `any` | — | — | No | Alias target for AWS resources (ELB, CloudFront, S3, etc.) |
| `weight` | `number` | — | — | No | Weighted routing weight (0-255) |
| `setIdentifier` | `string` | — | — | No | Set identifier for routing policies |
| `failover` | `string` | — | `PRIMARY`, `SECONDARY` | No | Failover routing type |
| `geoLocation` | `any` | — | — | No | Geolocation routing configuration |
| `healthCheckId` | `string` | — | — | No | Health check ID to associate with the record |
| `multiValueAnswer` | `boolean` | — | — | No | Whether this is a multivalue answer record |

[← Back to Index](README.md)
