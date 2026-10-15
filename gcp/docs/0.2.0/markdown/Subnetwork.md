# Subnetwork

## Example

```kite
resource Subnetwork example {
    name                  = "example-value"
    network               = "example-value"
    ipCidrRange           = "example-value"
    region                = "example-value"
    privateIpGoogleAccess = false
    description           = "example-value"
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `name` | `string` | — | — | No | The name of the subnetwork |
| `network` | `string` | — | — | No | The VPC network name or self-link this subnet belongs to |
| `ipCidrRange` | `string` | — | — | No | The IPv4 CIDR range for this subnetwork (e.g., 10.0.1.0/24) |
| `region` | `string` | — | — | No | The GCP region for this subnetwork (e.g., us-central1) |
| `privateIpGoogleAccess` | `boolean` | `false` | — | No | Whether VMs in this subnet can access Google APIs without external IP |
| `description` | `string` | — | — | No | An optional description of the subnetwork |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `subnetworkId` | `string` | *📥 importable* The unique subnetwork ID assigned by GCP |
| `selfLink` | `string` | The self-link URL of the subnetwork |
| `gatewayAddress` | `string` | The gateway address for default routing in this subnetwork |

[← Back to Index](README.md)
