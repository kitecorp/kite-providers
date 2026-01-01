package cloud.kitelang.provider.aws.networking;

import cloud.kitelang.api.annotations.Property;
import cloud.kitelang.api.annotations.TypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A security group rule for AWS Security Groups.
 *
 * Example:
 * <pre>
 * {
 *     protocol: "tcp",
 *     fromPort: 443,
 *     toPort: 443,
 *     cidrBlocks: ["0.0.0.0/0"],
 *     description: "Allow HTTPS"
 * }
 * </pre>
 *
 * @see <a href="https://docs.aws.amazon.com/vpc/latest/userguide/security-group-rules.html">AWS Security Group Rules Documentation</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("SecurityGroupRule")
public class SecurityGroupRule {

    @Property(description = "Protocol: 'tcp', 'udp', 'icmp', or '-1' for all",
              validValues = {"tcp", "udp", "icmp", "-1"})
    private String protocol;

    @Property(description = "Start of port range (0-65535). Use -1 for all ports when protocol is -1")
    private Integer fromPort;

    @Property(description = "End of port range (0-65535). Use -1 for all ports when protocol is -1")
    private Integer toPort;

    @Property(description = "List of CIDR blocks to allow/deny")
    private java.util.List<String> cidrBlocks;

    @Property(description = "List of IPv6 CIDR blocks")
    private java.util.List<String> ipv6CidrBlocks;

    @Property(description = "List of security group IDs to allow traffic from/to")
    private java.util.List<String> securityGroupIds;

    @Property(description = "Description of the rule")
    private String description;
}
