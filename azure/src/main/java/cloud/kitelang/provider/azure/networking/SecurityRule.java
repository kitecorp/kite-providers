package cloud.kitelang.provider.azure.networking;

import cloud.kitelang.api.annotations.Property;
import cloud.kitelang.api.annotations.TypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A security rule within an Azure Network Security Group.
 *
 * Example:
 * <pre>
 * {
 *     name: "allow-ssh",
 *     priority: 100,
 *     direction: "Inbound",
 *     access: "Allow",
 *     protocol: "Tcp",
 *     sourceAddressPrefix: "*",
 *     sourcePortRange: "*",
 *     destinationAddressPrefix: "*",
 *     destinationPortRange: "22"
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("SecurityRule")
public class SecurityRule {

    @Property(description = "Name of the security rule. Must be unique within the NSG", optional = false)
    private String name;

    @Property(description = "Priority of the rule (100-4096). Lower numbers have higher priority", optional = false)
    private Integer priority;

    @Property(description = "Direction of traffic",
              validValues = {"Inbound", "Outbound"},
              optional = false)
    private String direction;

    @Property(description = "Whether to allow or deny",
              validValues = {"Allow", "Deny"},
              optional = false)
    private String access;

    @Property(description = "Network protocol",
              validValues = {"Tcp", "Udp", "Icmp", "*"},
              optional = false)
    private String protocol;

    @Property(description = "Source address prefix (CIDR, tag, or *). Examples: 10.0.0.0/8, VirtualNetwork, Internet, *")
    private String sourceAddressPrefix;

    @Property(description = "Source port or range. Examples: 22, 80-443, *")
    private String sourcePortRange;

    @Property(description = "Destination address prefix (CIDR, tag, or *)")
    private String destinationAddressPrefix;

    @Property(description = "Destination port or range. Examples: 22, 80-443, *")
    private String destinationPortRange;

    @Property(description = "Optional description of the rule")
    private String description;
}
