# RouteTable

## Example

```kite
resource RouteTable example {
    vpcId  = "example-value"
    routes = ["item1", "item2"]
    tags   = { key = "value" }
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `vpcId` | `string` | — | — | No | The VPC ID where the route table will be created |
| `routes` | `list` | — | — | No | Routes in this route table |
| `tags` | `map` | — | — | No | Tags to apply to the route table |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `routeTableId` | `string` | — | — | No | *☁️ cloud-managed* The route table ID assigned by AWS |
| `ownerId` | `string` | — | — | No | *☁️ cloud-managed* The AWS account ID that owns the route table |
| `main` | `boolean` | — | — | No | *☁️ cloud-managed* Whether this is the main route table for the VPC |
| `associatedSubnetIds` | `list` | — | — | No | *☁️ cloud-managed* List of subnet IDs associated with this route table |

[← Back to Index](README.md)
