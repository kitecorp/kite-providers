# HostedZone

## Example

```kite
resource HostedZone example {
    name        = "example-value"
    comment     = "example-value"
    privateZone = false
    vpcId       = "example-value"
    vpcRegion   = "example-value"
    tags        = "..."
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `name` | `string` | — | — | No | The domain name for the hosted zone (e.g., 'example.com') |
| `comment` | `string` | — | — | No | A comment for the hosted zone |
| `privateZone` | `boolean` | `false` | — | No | Whether this is a private hosted zone |
| `vpcId` | `string` | — | — | No | VPC ID to associate with private hosted zone |
| `vpcRegion` | `string` | — | — | No | VPC region for private hosted zone |
| `tags` | `object` | — | — | No | Tags to apply to the hosted zone |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `hostedZoneId` | `string` | The hosted zone ID (without /hostedzone/ prefix) |
| `nameServers` | `any[]` | The name servers for the hosted zone |
| `resourceRecordSetCount` | `integer` | The number of resource record sets in the hosted zone |
| `callerReference` | `string` | A unique string that identifies the request to create the zone |
| `arn` | `string` | *📥 importable* The Amazon Resource Name (ARN) of the hosted zone |

[← Back to Index](README.md)
