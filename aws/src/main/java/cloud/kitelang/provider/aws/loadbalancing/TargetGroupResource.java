package cloud.kitelang.provider.aws.loadbalancing;

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
 * AWS Target Group resource for Load Balancers.
 *
 * Example usage:
 * <pre>
 * resource TargetGroup web {
 *     name = "web-targets"
 *     port = 80
 *     protocol = "HTTP"
 *     vpcId = vpc.vpcId
 *     targetType = "instance"
 *     healthCheck = {
 *         path: "/health",
 *         interval: 30,
 *         timeout: 5,
 *         healthyThreshold: 2,
 *         unhealthyThreshold: 3
 *     }
 *     tags = {
 *         Name: "web-targets"
 *     }
 * }
 * </pre>
 *
 * @see <a href="https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-target-groups.html">AWS Target Group Documentation</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("TargetGroup")
public class TargetGroupResource {

    @Property(description = "The name of the target group", optional = false)
    private String name;

    @Property(description = "The port on which targets receive traffic", optional = false)
    private Integer port;

    @Property(description = "The protocol for routing traffic",
              validValues = {"HTTP", "HTTPS", "TCP", "TLS", "UDP", "TCP_UDP", "GENEVE"})
    private String protocol = "HTTP";

    @Property(description = "The protocol version (ALB only)",
              validValues = {"HTTP1", "HTTP2", "GRPC"})
    private String protocolVersion = "HTTP1";

    @Property(description = "The VPC ID for the target group", optional = false)
    private String vpcId;

    @Property(description = "The target type",
              validValues = {"instance", "ip", "lambda", "alb"})
    private String targetType = "instance";

    @Property(description = "The IP address type",
              validValues = {"ipv4", "ipv6"})
    private String ipAddressType = "ipv4";

    @Property(description = "Health check configuration")
    private HealthCheck healthCheck;

    @Property(description = "Deregistration delay in seconds")
    private Integer deregistrationDelay = 300;

    @Property(description = "Slow start duration in seconds (ALB only)")
    private Integer slowStart = 0;

    @Property(description = "Stickiness configuration")
    private Stickiness stickiness;

    @Property(description = "Target IDs to register (instance IDs or IP addresses)")
    private List<String> targets;

    @Property(description = "Tags to apply to the target group")
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    @Cloud(importable = true)
    @Property(description = "The Amazon Resource Name (ARN) of the target group")
    private String arn;

    @Cloud
    @Property(description = "The load balancer ARNs attached to this target group")
    private List<String> loadBalancerArns;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HealthCheck {
        @Property(description = "The protocol for health checks")
        private String protocol;

        @Property(description = "The port for health checks")
        private String port = "traffic-port";

        @Property(description = "The path for HTTP/HTTPS health checks")
        private String path = "/";

        @Property(description = "The interval between health checks in seconds")
        private Integer interval = 30;

        @Property(description = "The timeout for health checks in seconds")
        private Integer timeout = 5;

        @Property(description = "The number of consecutive successful checks required")
        private Integer healthyThreshold = 5;

        @Property(description = "The number of consecutive failed checks required")
        private Integer unhealthyThreshold = 2;

        @Property(description = "The HTTP codes to consider healthy (e.g., '200' or '200-299')")
        private String matcher = "200";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stickiness {
        @Property(description = "Enable stickiness")
        private Boolean enabled;

        @Property(description = "Stickiness type",
                  validValues = {"lb_cookie", "source_ip", "app_cookie"})
        private String type;

        @Property(description = "Cookie duration in seconds (ALB lb_cookie only)")
        private Integer duration = 86400;

        @Property(description = "Application cookie name (ALB app_cookie only)")
        private String cookieName;
    }
}
