# CloudStorageBucket

## Example

```kite
resource CloudStorageBucket example {
    name                     = "example-value"
    location                 = "US"
    storageClass             = "STANDARD"
    versioning               = false
    labels                   = "..."
    uniformBucketLevelAccess = true
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `name` | `string` | — | — | No | Globally unique bucket name |
| `location` | `string` | `US` | — | No | The location of the bucket (e.g., US, EU, us-central1) |
| `storageClass` | `string` | `STANDARD` | `STANDARD`, `NEARLINE`, `COLDLINE`, `ARCHIVE` | No | The storage class for the bucket |
| `versioning` | `boolean` | `false` | — | No | Enable object versioning |
| `labels` | `object` | — | — | No | Labels to apply to the bucket |
| `uniformBucketLevelAccess` | `boolean` | `true` | — | No | Whether uniform bucket-level access is enabled |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `selfLink` | `string` | *📥 importable* The self-link URL of the bucket |
| `timeCreated` | `string` | The creation time of the bucket |

[← Back to Index](README.md)
