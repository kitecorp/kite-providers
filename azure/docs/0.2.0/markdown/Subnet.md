# Subnet

## Example

```kite
resource Subnet example {
    name                              = "example-value"
    resourceGroup                     = "example-value"
    vnetName                          = "example-value"
    addressPrefix                     = "example-value"
    networkSecurityGroupId            = "example-value"
    routeTableId                      = "example-value"
    privateEndpointNetworkPolicies    = true
    privateLinkServiceNetworkPolicies = true
    serviceEndpoints                  = "..."
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `name` | `string` | — | — | No | The name of the subnet |
| `resourceGroup` | `string` | — | — | No | The Azure resource group name |
| `vnetName` | `string` | — | — | No | The name of the parent Virtual Network |
| `addressPrefix` | `string` | — | — | No | The address prefix for the subnet in CIDR notation (e.g., 10.0.1.0/24) |
| `networkSecurityGroupId` | `string` | — | — | No | The ID of the Network Security Group to associate |
| `routeTableId` | `string` | — | — | No | The ID of the Route Table to associate |
| `privateEndpointNetworkPolicies` | `boolean` | `true` | — | No | Enable private endpoint network policies |
| `privateLinkServiceNetworkPolicies` | `boolean` | `true` | — | No | Enable private link service network policies |
| `serviceEndpoints` | `any[]` | — | — | No | Service endpoints to enable (e.g., Microsoft.Storage, Microsoft.Sql) |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `id` | `string` | *📥 importable* The Azure resource ID of the subnet |
| `provisioningState` | `string` | The provisioning state of the subnet |
| `purpose` | `string` | The purpose of the subnet (e.g., for private endpoints) |

[← Back to Index](README.md)
