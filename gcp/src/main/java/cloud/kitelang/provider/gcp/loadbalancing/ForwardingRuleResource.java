package cloud.kitelang.provider.gcp.loadbalancing;

import cloud.kitelang.api.annotations.Cloud;
import cloud.kitelang.api.annotations.Property;
import cloud.kitelang.api.annotations.TypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * GCP Forwarding Rule - defines how traffic is directed to a backend service or target.
 *
 * <p>Forwarding rules are the GCP equivalent of load balancer listeners.
 * They match traffic by IP, protocol, and port, then forward it to a target.</p>
 *
 * Example usage:
 * <pre>
 * resource ForwardingRule web {
 *     name = "web-forwarding-rule"
 *     target = targetPool.selfLink
 *     portRange = "80"
 *     ipProtocol = "TCP"
 *     loadBalancingScheme = "EXTERNAL"
 * }
 * </pre>
 *
 * @see <a href="https://cloud.google.com/load-balancing/docs/forwarding-rule-concepts">GCP Forwarding Rule Documentation</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("ForwardingRule")
public class ForwardingRuleResource {

    @Property(description = "The name of the forwarding rule", optional = false)
    private String name;

    @Property(description = "The target resource URL (target pool, proxy, or backend service)")
    private String target;

    @Property(description = "The port range for this forwarding rule (e.g., '80', '8080-8090')")
    private String portRange;

    @Property(description = "The IP protocol for this forwarding rule",
              validValues = {"TCP", "UDP", "ESP", "AH", "SCTP", "ICMP"})
    private String ipProtocol = "TCP";

    @Property(description = "The load balancing scheme",
              validValues = {"EXTERNAL", "EXTERNAL_MANAGED", "INTERNAL", "INTERNAL_MANAGED", "INTERNAL_SELF_MANAGED"})
    private String loadBalancingScheme = "EXTERNAL";

    @Property(description = "The VPC network for internal load balancers")
    private String network;

    @Property(description = "The subnetwork for internal load balancers")
    private String subnetwork;

    @Property(description = "The GCP region for this forwarding rule (regional rules only)")
    private String region;

    @Property(description = "Labels to apply to the forwarding rule")
    private Map<String, String> labels;

    @Property(description = "An optional description")
    private String description;

    // --- Cloud-managed properties (read-only) ---

    @Cloud(importable = true)
    @Property(description = "The unique ID of the forwarding rule")
    private String forwardingRuleId;

    @Cloud
    @Property(description = "The self-link URL of the forwarding rule")
    private String selfLink;

    @Cloud
    @Property(description = "The IP address assigned to this forwarding rule")
    private String ipAddress;
}
