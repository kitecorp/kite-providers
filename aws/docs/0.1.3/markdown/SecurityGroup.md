# SecurityGroup

## Example

```kite
resource SecurityGroup example {
    name        = "example-value"
    description = "example-value"
    vpcId       = "example-value"
    ingress     = ["item1", "item2"]
    egress      = ["item1", "item2"]
    tags        = { key: "value" }
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `name` | `string` | — | — | No | Name of the security group |
| `description` | `string` | — | — | No | Description of the security group (required by AWS) |
| `vpcId` | `string` | — | — | No | VPC ID where the security group will be created |
| `ingress` | `list` | — | — | No | Inbound rules allowing traffic into resources |
| `egress` | `list` | — | — | No | Outbound rules allowing traffic from resources |
| `tags` | `map` | — | — | No | Tags to apply to the security group |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `securityGroupId` | `string` | The security group ID assigned by AWS |
| `ownerId` | `string` | The AWS account ID that owns the security group |
| `arn` | `string` | *📥 importable* The Amazon Resource Name (ARN) |

[← Back to Index](README.md)
