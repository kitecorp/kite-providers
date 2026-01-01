package cloud.kitelang.provider.aws.networking;

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
 *
 * @see <a href="https://docs.aws.amazon.com/vpc/latest/userguide/vpc-security-groups.html">AWS Security Group Documentation</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("SecurityGroup")
public class SecurityGroupResource {

    @Property(description = "Name of the security group")
    private String name;

    @Property(description = "Description of the security group (required by AWS)")
    private String description;

    @Property(description = "VPC ID where the security group will be created")
    private String vpcId;

    @Property(description = "Inbound rules allowing traffic into resources")
    private List<SecurityGroupRule> ingress;

    @Property(description = "Outbound rules allowing traffic from resources")
    private List<SecurityGroupRule> egress;

    @Property(description = "Tags to apply to the security group")
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    @Cloud
    @Property(description = "The security group ID assigned by AWS")
    private String securityGroupId;

    @Cloud
    @Property(description = "The AWS account ID that owns the security group")
    private String ownerId;

    @Cloud(importable = true)
    @Property(description = "The Amazon Resource Name (ARN)")
    private String arn;
}
