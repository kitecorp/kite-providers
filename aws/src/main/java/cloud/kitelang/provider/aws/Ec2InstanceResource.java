package cloud.kitelang.provider.aws;

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

    /**
     * The AMI ID to use for the instance.
     * Required.
     */
    @Property
    private String ami;

    /**
     * The instance type (e.g., t3.micro, m5.large).
     * Required.
     */
    @Property
    private String instanceType;

    /**
     * The subnet ID to launch the instance in.
     * Required for VPC.
     */
    @Property
    private String subnetId;

    /**
     * List of security group IDs to attach.
     */
    @Property
    private List<String> securityGroupIds;

    /**
     * The name of the key pair for SSH access.
     */
    @Property
    private String keyName;

    /**
     * Whether to associate a public IP address.
     * Only works in subnets with auto-assign public IP.
     */
    @Property
    private Boolean associatePublicIpAddress;

    /**
     * The IAM instance profile name or ARN.
     */
    @Property
    private String iamInstanceProfile;

    /**
     * User data script for instance initialization.
     * Will be base64 encoded automatically.
     */
    @Property
    private String userData;

    /**
     * The availability zone to launch in.
     * If not specified, AWS chooses one.
     */
    @Property
    private String availabilityZone;

    /**
     * Enable detailed monitoring.
     * Default: false (basic monitoring)
     */
    @Property
    private Boolean monitoring;

    /**
     * The tenancy of the instance: default, dedicated, or host.
     */
    @Property
    private String tenancy;

    /**
     * Root volume size in GiB.
     */
    @Property
    private Integer rootVolumeSize;

    /**
     * Root volume type: gp2, gp3, io1, io2, st1, sc1.
     */
    @Property
    private String rootVolumeType;

    /**
     * Whether to delete root volume on termination.
     * Default: true
     */
    @Property
    private Boolean deleteRootVolumeOnTermination;

    /**
     * Tags to apply to the instance.
     */
    @Property
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    /**
     * The instance ID assigned by AWS.
     */
    @Cloud
    @Property
    private String instanceId;

    /**
     * The current state of the instance.
     * Values: pending, running, stopping, stopped, shutting-down, terminated
     */
    @Cloud
    @Property
    private String state;

    /**
     * The public IP address (if assigned).
     */
    @Cloud
    @Property
    private String publicIp;

    /**
     * The private IP address.
     */
    @Cloud
    @Property
    private String privateIp;

    /**
     * The public DNS name.
     */
    @Cloud
    @Property
    private String publicDnsName;

    /**
     * The private DNS name.
     */
    @Cloud
    @Property
    private String privateDnsName;

    /**
     * The VPC ID the instance is in.
     */
    @Cloud
    @Property
    private String vpcId;

    /**
     * The architecture: x86_64 or arm64.
     */
    @Cloud
    @Property
    private String architecture;

    /**
     * The root device name.
     */
    @Cloud
    @Property
    private String rootDeviceName;
}
