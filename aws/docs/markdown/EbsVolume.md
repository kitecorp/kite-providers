# EbsVolume

## Example

```kite
resource EbsVolume example {
    availabilityZone   = "example-value"
    size               = 42
    volumeType         = "gp3"
    iops               = 42
    throughput         = 42
    encrypted          = false
    kmsKeyId           = "example-value"
    snapshotId         = "example-value"
    multiAttachEnabled = false
    tags               = { key = "value" }
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `availabilityZone` | `string` | — | — | No | The Availability Zone in which to create the volume |
| `size` | `integer` | — | — | No | The size of the volume in GiBs |
| `volumeType` | `string` | `gp3` | `gp2`, `gp3`, `io1`, `io2`, `st1`, `sc1`, `standard` | No | The volume type |
| `iops` | `integer` | — | — | No | The number of I/O operations per second (IOPS) |
| `throughput` | `integer` | — | — | No | The throughput in MiB/s. Only valid for gp3 volumes |
| `encrypted` | `boolean` | `false` | — | No | Whether the volume should be encrypted |
| `kmsKeyId` | `string` | — | — | No | The ARN of the KMS key to use for encryption |
| `snapshotId` | `string` | — | — | No | The snapshot ID to create the volume from |
| `multiAttachEnabled` | `boolean` | `false` | — | No | Enable Multi-Attach for io1/io2 volumes |
| `tags` | `map` | — | — | No | Tags to apply to the volume |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `volumeId` | `string` | — | — | No | *☁️ cloud-managed* The volume ID assigned by AWS |
| `arn` | `string` | — | — | No | *☁️ cloud-managed, 📥 importable* The Amazon Resource Name (ARN) of the volume |
| `state` | `string` | — | — | No | *☁️ cloud-managed* The current state of the volume |
| `createTime` | `string` | — | — | No | *☁️ cloud-managed* The time the volume was created |
| `attachments` | `string` | — | — | No | *☁️ cloud-managed* Information about the volume attachments |

[← Back to Index](README.md)
