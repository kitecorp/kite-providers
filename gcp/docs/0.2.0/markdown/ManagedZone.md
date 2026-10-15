# ManagedZone

## Example

```kite
resource ManagedZone example {
    name        = "example-value"
    dnsName     = "example-value"
    description = "example-value"
    labels      = "..."
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `name` | `string` | — | — | No | The name of the managed zone (must be unique within the project) |
| `dnsName` | `string` | — | — | No | The DNS name of the zone (must end with a dot, e.g., 'example.com.') |
| `description` | `string` | — | — | No | A description for the managed zone |
| `labels` | `object` | — | — | No | Labels to apply to the managed zone |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `managedZoneId` | `string` | *📥 importable* The unique managed zone ID assigned by GCP |
| `nameServers` | `any[]` | The name servers assigned to this managed zone |
| `creationTime` | `string` | The creation time of the managed zone |

[← Back to Index](README.md)
