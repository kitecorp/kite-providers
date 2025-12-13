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
 * AWS Security Group - stateful firewall for EC2 instances.
 *
 * Example usage:
 * <pre>
 * resource SecurityGroup web {
 *     name = "web-sg"
 *     description = "Security group for web servers"
 *     vpcId = vpc.vpcId
 *     ingress = [
 *         {
 *             protocol: "tcp",
 *             fromPort: 80,
 *             toPort: 80,
 *             cidrBlocks: ["0.0.0.0/0"],
 *             description: "Allow HTTP"
 *         },
 *         {
 *             protocol: "tcp",
 *             fromPort: 443,
 *             toPort: 443,
 *             cidrBlocks: ["0.0.0.0/0"],
 *             description: "Allow HTTPS"
 *         }
 *     ]
 *     egress = [
 *         {
 *             protocol: "-1",
 *             fromPort: -1,
 *             toPort: -1,
 *             cidrBlocks: ["0.0.0.0/0"],
 *             description: "Allow all outbound"
 *         }
 *     ]
 *     tags = {
 *         Name: "web-sg",
 *         Environment: "production"
 *     }
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("SecurityGroup")
public class SecurityGroupResource {

    /**
     * Name of the security group.
     * Required.
     */
    @Property
    private String name;

    /**
     * Description of the security group.
     * Required by AWS.
     */
    @Property
    private String description;

    /**
     * VPC ID where the security group will be created.
     * Required for VPC security groups.
     */
    @Property
    private String vpcId;

    /**
     * Inbound rules (ingress).
     */
    @Property
    private List<SecurityGroupRule> ingress;

    /**
     * Outbound rules (egress).
     */
    @Property
    private List<SecurityGroupRule> egress;

    /**
     * Tags to apply to the security group.
     */
    @Property
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    /**
     * The security group ID assigned by AWS.
     */
    @Cloud
    @Property
    private String securityGroupId;

    /**
     * The AWS account ID that owns the security group.
     */
    @Cloud
    @Property
    private String ownerId;

    /**
     * The Amazon Resource Name (ARN) of the security group.
     */
    @Cloud
    @Property
    private String arn;
}
