# ComputeInstance

## Example

```kite
resource ComputeInstance example {
    name             = "example-value"
    machineType      = "example-value"
    zone             = "example-value"
    imageFamily      = "example-value"
    imageProject     = "example-value"
    networkInterface = "example-value"
    subnetwork       = "example-value"
    tags             = "..."
    labels           = "..."
    diskSizeGb       = 3.14
    diskType         = "pd-standard"
    externalIp       = true
    startupScript    = "example-value"
    serviceAccount   = "example-value"
    scopes           = "..."
    preemptible      = false
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `name` | `string` | — | — | No | The name of the instance |
| `machineType` | `string` | — | — | No | The machine type (e.g., e2-medium, n1-standard-1) |
| `zone` | `string` | — | — | No | The zone where the instance will be created (e.g., us-central1-a) |
| `imageFamily` | `string` | — | — | No | The image family to use for the boot disk (e.g., debian-12) |
| `imageProject` | `string` | — | — | No | The project containing the image family (e.g., debian-cloud) |
| `networkInterface` | `string` | — | — | No | The VPC network name or self-link for the primary network interface |
| `subnetwork` | `string` | — | — | No | The subnetwork name or self-link for the primary network interface |
| `tags` | `any[]` | — | — | No | Network tags for firewall rules |
| `labels` | `object` | — | — | No | Labels to apply to the instance |
| `diskSizeGb` | `number` | — | — | No | Boot disk size in GB |
| `diskType` | `string` | — | `pd-standard`, `pd-ssd`, `pd-balanced` | No | Boot disk type |
| `externalIp` | `boolean` | `true` | — | No | Whether to assign an external IP address |
| `startupScript` | `string` | — | — | No | Startup script content |
| `serviceAccount` | `string` | — | — | No | The service account email for the instance |
| `scopes` | `any[]` | — | — | No | OAuth2 scopes for the service account |
| `preemptible` | `boolean` | `false` | — | No | Whether to enable preemptible/spot scheduling |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `instanceId` | `string` | *📥 importable* The unique instance ID assigned by GCP |
| `status` | `string` | The instance status (PROVISIONING, STAGING, RUNNING, STOPPING, STOPPED, TERMINATED) |
| `selfLink` | `string` | The self-link URL of the instance |
| `externalIpAddress` | `string` | The external (public) IP address |
| `internalIpAddress` | `string` | The internal (private) IP address |

[← Back to Index](README.md)
