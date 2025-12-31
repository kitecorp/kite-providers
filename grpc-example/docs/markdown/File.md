# File

## Example

```kite
resource File example {
    path        = "example-value"
    content     = "example-value"
    permissions = "example-value"
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `path` | `string` | — | — | No |  |
| `content` | `string` | — | — | No |  |
| `permissions` | `string` | — | — | No |  |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `checksum` | `string` | — | — | No | *☁️ cloud-managed*  |
| `lastModified` | `string` | — | — | No | *☁️ cloud-managed*  |

[← Back to Index](README.md)
