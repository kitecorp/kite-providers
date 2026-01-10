# IamRole

## Example

```kite
resource IamRole example {
    name               = "example-value"
    description        = "example-value"
    assumeRolePolicy   = "example-value"
    path               = "example-value"
    maxSessionDuration = 42
    managedPolicyArns  = ["item1", "item2"]
    inlinePolicies     = { key: "value" }
    tags               = { key: "value" }
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `name` | `string` | — | — | No | Name of the IAM role |
| `description` | `string` | — | — | No | Description of the role |
| `assumeRolePolicy` | `string` | — | — | No | The trust policy that grants an entity permission to assume the role (JSON) |
| `path` | `string` | — | — | No | Path for the role (default: /) |
| `maxSessionDuration` | `integer` | — | — | No | Maximum session duration in seconds (3600-43200) |
| `managedPolicyArns` | `list` | — | — | No | ARNs of AWS managed policies to attach |
| `inlinePolicies` | `map` | — | — | No | Inline policies to embed in the role (name -> policy JSON) |
| `tags` | `map` | — | — | No | Tags to apply to the role |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `roleId` | `string` | The stable and unique ID for the role |
| `arn` | `string` | *📥 importable* The Amazon Resource Name (ARN) of the role |
| `createDate` | `string` | The date and time when the role was created |

[← Back to Index](README.md)
