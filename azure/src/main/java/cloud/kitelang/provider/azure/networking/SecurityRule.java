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

    /**
     * Name of the security rule.
     * Must be unique within the NSG.
     */
    @Property
    private String name;

    /**
     * Priority of the rule (100-4096).
     * Lower numbers have higher priority.
     */
    @Property
    private Integer priority;

    /**
     * Direction of traffic: "Inbound" or "Outbound".
     */
    @Property
    private String direction;

    /**
     * Whether to allow or deny: "Allow" or "Deny".
     */
    @Property
    private String access;

    /**
     * Network protocol: "Tcp", "Udp", "Icmp", or "*" for all.
     */
    @Property
    private String protocol;

    /**
     * Source address prefix (CIDR, tag, or "*").
     * Examples: "10.0.0.0/8", "VirtualNetwork", "Internet", "*"
     */
    @Property
    private String sourceAddressPrefix;

    /**
     * Source port or range.
     * Examples: "22", "80-443", "*"
     */
    @Property
    private String sourcePortRange;

    /**
     * Destination address prefix (CIDR, tag, or "*").
     */
    @Property
    private String destinationAddressPrefix;

    /**
     * Destination port or range.
     * Examples: "22", "80-443", "*"
     */
    @Property
    private String destinationPortRange;

    /**
     * Optional description of the rule.
     */
    @Property
    private String description;
}
