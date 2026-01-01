# Ec2Instance

## Example

```kite
resource Ec2Instance example {
    ami                           = "example-value"
    instanceType                  = "example-value"
    subnetId                      = "example-value"
    securityGroupIds              = ["item1", "item2"]
    keyName                       = "example-value"
    associatePublicIpAddress      = false
    iamInstanceProfile            = "example-value"
    userData                      = "example-value"
    availabilityZone              = "example-value"
    monitoring                    = false
    tenancy                       = "default"
    rootVolumeSize                = 42
    rootVolumeType                = "gp3"
    deleteRootVolumeOnTermination = true
    tags                          = { key = "value" }
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `ami` | `string` | — | — | No | The AMI ID to use for the instance |
| `instanceType` | `string` | — | — | No | The instance type (e.g., t3.micro, m5.large) |
| `subnetId` | `string` | — | — | No | The subnet ID to launch the instance in |
| `securityGroupIds` | `list` | — | — | No | List of security group IDs to attach |
| `keyName` | `string` | — | — | No | The name of the key pair for SSH access |
| `associatePublicIpAddress` | `boolean` | `false` | — | No | Whether to associate a public IP address |
| `iamInstanceProfile` | `string` | — | — | No | The IAM instance profile name or ARN |
| `userData` | `string` | — | — | No | User data script for instance initialization (base64 encoded) |
| `availabilityZone` | `string` | — | — | No | The availability zone to launch in |
| `monitoring` | `boolean` | `false` | — | No | Enable detailed monitoring |
| `tenancy` | `string` | `default` | `default`, `dedicated`, `host` | No | Instance tenancy option |
| `rootVolumeSize` | `integer` | — | — | No | Root volume size in GiB |
| `rootVolumeType` | `string` | `gp3` | `gp2`, `gp3`, `io1`, `io2`, `st1`, `sc1` | No | Root volume type |
| `deleteRootVolumeOnTermination` | `boolean` | `true` | — | No | Delete root volume on termination |
| `tags` | `map` | — | — | No | Tags to apply to the instance |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `instanceId` | `string` | The instance ID assigned by AWS |
| `state` | `string` | Instance state: pending, running, stopping, stopped, terminated |
| `publicIp` | `string` | The public IP address (if assigned) |
| `privateIp` | `string` | The private IP address |
| `publicDnsName` | `string` | The public DNS name |
| `privateDnsName` | `string` | The private DNS name |
| `vpcId` | `string` | The VPC ID the instance is in |
| `architecture` | `string` | The architecture: x86_64 or arm64 |
| `rootDeviceName` | `string` | The root device name |

[← Back to Index](README.md)
