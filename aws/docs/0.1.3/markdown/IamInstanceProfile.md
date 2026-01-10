# IamInstanceProfile

## Example

```kite
resource IamInstanceProfile example {
    name = "example-value"
    path = "example-value"
    role = "example-value"
    tags = { key: "value" }
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `name` | `string` | — | — | No | Name of the instance profile |
| `path` | `string` | — | — | No | Path for the instance profile (default: /) |
| `role` | `string` | — | — | No | Name of the IAM role to associate with the instance profile |
| `tags` | `map` | — | — | No | Tags to apply to the instance profile |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `instanceProfileId` | `string` | The stable and unique ID for the instance profile |
| `arn` | `string` | *📥 importable* The Amazon Resource Name (ARN) of the instance profile |
| `createDate` | `string` | The date and time when the instance profile was created |

[← Back to Index](README.md)
