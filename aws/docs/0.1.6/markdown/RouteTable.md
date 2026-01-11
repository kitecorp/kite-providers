# RouteTable

## Example

```kite
resource RouteTable example {
    vpcId  = "example-value"
    routes = "..."
    tags   = "..."
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `vpcId` | `string` | — | — | No | The VPC ID where the route table will be created |
| `routes` | `any[]` | — | — | No | Routes in this route table |
| `tags` | `object` | — | — | No | Tags to apply to the route table |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `routeTableId` | `string` | The route table ID assigned by AWS |
| `ownerId` | `string` | The AWS account ID that owns the route table |
| `main` | `boolean` | Whether this is the main route table for the VPC |
| `associatedSubnetIds` | `any[]` | List of subnet IDs associated with this route table |

[← Back to Index](README.md)
