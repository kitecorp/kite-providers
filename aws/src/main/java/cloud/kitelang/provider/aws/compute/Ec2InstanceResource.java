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
 *
 * @see <a href="https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/concepts.html">AWS EC2 Documentation</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("Ec2Instance")
public class Ec2InstanceResource {

    @Property(description = "The AMI ID to use for the instance", optional = false)
    private String ami;

    @Property(description = "The instance type (e.g., t3.micro, m5.large)", optional = false)
    private String instanceType;

    @Property(description = "The subnet ID to launch the instance in")
    private String subnetId;

    @Property(description = "List of security group IDs to attach")
    private List<String> securityGroupIds;

    @Property(description = "The name of the key pair for SSH access")
    private String keyName;

    @Property(description = "Whether to associate a public IP address")
    private Boolean associatePublicIpAddress = false;

    @Property(description = "The IAM instance profile name or ARN")
    private String iamInstanceProfile;

    @Property(description = "User data script for instance initialization (base64 encoded)")
    private String userData;

    @Property(description = "The availability zone to launch in")
    private String availabilityZone;

    @Property(description = "Enable detailed monitoring")
    private Boolean monitoring = false;

    @Property(description = "Instance tenancy option",
              validValues = {"default", "dedicated", "host"})
    private String tenancy = "default";

    @Property(description = "Root volume size in GiB")
    private Integer rootVolumeSize;

    @Property(description = "Root volume type",
              validValues = {"gp2", "gp3", "io1", "io2", "st1", "sc1"})
    private String rootVolumeType = "gp3";

    @Property(description = "Delete root volume on termination")
    private Boolean deleteRootVolumeOnTermination = true;

    @Property(description = "Tags to apply to the instance")
    private Map<String, String> tags;

    @Property(description = "Whether the instance is optimized for Amazon EBS I/O")
    private Boolean ebsOptimized = false;

    @Property(description = "CPU credit option for burstable instances",
              validValues = {"standard", "unlimited"})
    private String cpuCredits;

    @Property(description = "Whether instance hibernation is enabled")
    private Boolean hibernation = false;

    @Property(description = "The name of the placement group for cluster networking")
    private String placementGroup;

    @Property(description = "HTTP tokens requirement for instance metadata service (IMDSv2)",
              validValues = {"optional", "required"})
    private String metadataHttpTokens = "optional";

    @Property(description = "HTTP PUT response hop limit for instance metadata requests (1-64)")
    private Integer metadataHttpPutResponseHopLimit = 1;

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
