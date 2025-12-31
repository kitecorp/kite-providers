package cloud.kitelang.provider.aws.compute;

import cloud.kitelang.api.annotations.Cloud;
import cloud.kitelang.api.annotations.Property;
import cloud.kitelang.api.annotations.TypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * AWS EC2 Instance - a virtual server in the cloud.
 *
 * Example usage:
 * <pre>
 * resource Ec2Instance web {
 *     ami = "ami-0c55b159cbfafe1f0"
 *     instanceType = "t3.micro"
 *     subnetId = subnet.subnetId
 *     securityGroupIds = [sg.securityGroupId]
 *     keyName = "my-key"
 *     associatePublicIpAddress = true
 *     tags = {
 *         Name: "web-server",
 *         Environment: "production"
 *     }
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("Ec2Instance")
public class Ec2InstanceResource {

    @Property(description = "The AMI ID to use for the instance")
    private String ami;

    @Property(description = "The instance type (e.g., t3.micro, m5.large)")
    private String instanceType;

    @Property(description = "The subnet ID to launch the instance in")
    private String subnetId;

    @Property(description = "List of security group IDs to attach")
    private List<String> securityGroupIds;

    @Property(description = "The name of the key pair for SSH access")
    private String keyName;

    @Property(description = "Whether to associate a public IP address")
    private Boolean associatePublicIpAddress;

    @Property(description = "The IAM instance profile name or ARN")
    private String iamInstanceProfile;

    @Property(description = "User data script for instance initialization (base64 encoded)")
    private String userData;

    @Property(description = "The availability zone to launch in")
    private String availabilityZone;

    @Property(description = "Enable detailed monitoring. Default: false")
    private Boolean monitoring;

    @Property(description = "Tenancy: default, dedicated, or host")
    private String tenancy;

    @Property(description = "Root volume size in GiB")
    private Integer rootVolumeSize;

    @Property(description = "Root volume type: gp2, gp3, io1, io2, st1, sc1")
    private String rootVolumeType;

    @Property(description = "Delete root volume on termination. Default: true")
    private Boolean deleteRootVolumeOnTermination;

    @Property(description = "Tags to apply to the instance")
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    @Cloud
    @Property(description = "The instance ID assigned by AWS")
    private String instanceId;

    @Cloud
    @Property(description = "Instance state: pending, running, stopping, stopped, terminated")
    private String state;

    @Cloud
    @Property(description = "The public IP address (if assigned)")
    private String publicIp;

    @Cloud
    @Property(description = "The private IP address")
    private String privateIp;

    @Cloud
    @Property(description = "The public DNS name")
    private String publicDnsName;

    @Cloud
    @Property(description = "The private DNS name")
    private String privateDnsName;

    @Cloud
    @Property(description = "The VPC ID the instance is in")
    private String vpcId;

    @Cloud
    @Property(description = "The architecture: x86_64 or arm64")
    private String architecture;

    @Cloud
    @Property(description = "The root device name")
    private String rootDeviceName;
}
