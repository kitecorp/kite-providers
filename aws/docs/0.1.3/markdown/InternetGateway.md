# InternetGateway

## Example

```kite
resource InternetGateway example {
    vpcId = "example-value"
    tags  = { key: "value" }
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `vpcId` | `string` | — | — | No | The VPC ID to attach the internet gateway to |
| `tags` | `map` | — | — | No | Tags to apply to the internet gateway |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `internetGatewayId` | `string` | The internet gateway ID assigned by AWS |
| `ownerId` | `string` | The AWS account ID that owns the internet gateway |
| `attachmentState` | `string` | Attachment state (available, attaching, detaching) |

[← Back to Index](README.md)
