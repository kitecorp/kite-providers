package cloud.kitelang.provider.aws;

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
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("SecurityGroupRule")
public class SecurityGroupRule {

    /**
     * Protocol: "tcp", "udp", "icmp", or "-1" for all.
     */
    @Property
    private String protocol;

    /**
     * Start of port range (0-65535).
     * Use -1 for all ports when protocol is -1.
     */
    @Property
    private Integer fromPort;

    /**
     * End of port range (0-65535).
     * Use -1 for all ports when protocol is -1.
     */
    @Property
    private Integer toPort;

    /**
     * List of CIDR blocks to allow/deny.
     * Example: ["10.0.0.0/8", "0.0.0.0/0"]
     */
    @Property
    private java.util.List<String> cidrBlocks;

    /**
     * List of IPv6 CIDR blocks.
     */
    @Property
    private java.util.List<String> ipv6CidrBlocks;

    /**
     * List of security group IDs to allow traffic from/to.
     * For self-referencing, use the security group's own ID.
     */
    @Property
    private java.util.List<String> securityGroupIds;

    /**
     * Description of the rule.
     */
    @Property
    private String description;
}
