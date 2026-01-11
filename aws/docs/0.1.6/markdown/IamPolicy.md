# IamPolicy

## Example

```kite
resource IamPolicy example {
    name        = "example-value"
    description = "example-value"
    path        = "example-value"
    policy      = "example-value"
    tags        = "..."
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `name` | `string` | — | — | No | Name of the policy |
| `description` | `string` | — | — | No | Description of the policy |
| `path` | `string` | — | — | No | Path for the policy (default: /) |
| `policy` | `string` | — | — | No | The JSON policy document |
| `tags` | `object` | — | — | No | Tags to apply to the policy |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `policyId` | `string` | The stable and unique ID for the policy |
| `arn` | `string` | *📥 importable* The Amazon Resource Name (ARN) of the policy |
| `createDate` | `string` | The date and time when the policy was created |
| `attachmentCount` | `number` | The number of entities (users, groups, roles) attached to this policy |
| `defaultVersionId` | `string` | The default version ID of the policy |

[← Back to Index](README.md)
