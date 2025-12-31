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
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("TargetGroup")
public class TargetGroupResource {

    @Property(description = "The name of the target group")
    private String name;

    @Property(description = "The port on which targets receive traffic")
    private Integer port;

    @Property(description = "The protocol: HTTP, HTTPS, TCP, TLS, UDP, TCP_UDP, GENEVE")
    private String protocol;

    @Property(description = "The protocol version (ALB only): HTTP1, HTTP2, GRPC")
    private String protocolVersion;

    @Property(description = "The VPC ID for the target group")
    private String vpcId;

    @Property(description = "The target type: instance, ip, lambda, alb. Default: instance")
    private String targetType;

    @Property(description = "The IP address type: ipv4 or ipv6. Default: ipv4")
    private String ipAddressType;

    @Property(description = "Health check configuration")
    private HealthCheck healthCheck;

    @Property(description = "Deregistration delay in seconds. Default: 300")
    private Integer deregistrationDelay;

    @Property(description = "Slow start duration in seconds (ALB only). Default: 0")
    private Integer slowStart;

    @Property(description = "Stickiness configuration")
    private Stickiness stickiness;

    @Property(description = "Target IDs to register (instance IDs or IP addresses)")
    private List<String> targets;

    @Property(description = "Tags to apply to the target group")
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    @Cloud
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
        /**
         * The protocol for health checks.
         * Default: same as target group protocol.
         */
        private String protocol;

        /**
         * The port for health checks.
         * Default: traffic-port
         */
        private String port;

        /**
         * The path for HTTP/HTTPS health checks.
         * Default: /
         */
        private String path;

        /**
         * The interval between health checks in seconds.
         * Default: 30
         */
        private Integer interval;

        /**
         * The timeout for health checks in seconds.
         * Default: 5
         */
        private Integer timeout;

        /**
         * The number of consecutive successful checks required.
         * Default: 5
         */
        private Integer healthyThreshold;

        /**
         * The number of consecutive failed checks required.
         * Default: 2
         */
        private Integer unhealthyThreshold;

        /**
         * The HTTP codes to consider healthy (e.g., "200" or "200-299").
         * Default: 200
         */
        private String matcher;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stickiness {
        /**
         * Enable stickiness.
         */
        private Boolean enabled;

        /**
         * Stickiness type.
         * Valid values: lb_cookie (ALB), source_ip (NLB).
         */
        private String type;

        /**
         * Cookie duration in seconds (ALB lb_cookie only).
         * Default: 86400
         */
        private Integer duration;

        /**
         * Application cookie name (ALB app_cookie only).
         */
        private String cookieName;
    }
}
