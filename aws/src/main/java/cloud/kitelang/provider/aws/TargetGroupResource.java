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

    /**
     * The name of the target group.
     * Required.
     */
    @Property
    private String name;

    /**
     * The port on which the targets receive traffic.
     * Required for instance/ip target types.
     */
    @Property
    private Integer port;

    /**
     * The protocol to use for routing traffic to the targets.
     * Valid values: HTTP, HTTPS, TCP, TLS, UDP, TCP_UDP, GENEVE.
     */
    @Property
    private String protocol;

    /**
     * The protocol version (ALB only).
     * Valid values: HTTP1, HTTP2, GRPC.
     * Default: HTTP1
     */
    @Property
    private String protocolVersion;

    /**
     * The VPC ID for the target group.
     * Required for instance/ip target types.
     */
    @Property
    private String vpcId;

    /**
     * The type of target.
     * Valid values: instance, ip, lambda, alb.
     * Default: instance
     */
    @Property
    private String targetType;

    /**
     * The IP address type.
     * Valid values: ipv4, ipv6.
     * Default: ipv4
     */
    @Property
    private String ipAddressType;

    /**
     * Health check configuration.
     */
    @Property
    private HealthCheck healthCheck;

    /**
     * Deregistration delay in seconds.
     * Default: 300
     */
    @Property
    private Integer deregistrationDelay;

    /**
     * Slow start duration in seconds (ALB only).
     * Default: 0 (disabled)
     */
    @Property
    private Integer slowStart;

    /**
     * Stickiness configuration.
     */
    @Property
    private Stickiness stickiness;

    /**
     * Target IDs to register (instance IDs or IP addresses).
     */
    @Property
    private List<String> targets;

    /**
     * Tags to apply to the target group.
     */
    @Property
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    /**
     * The Amazon Resource Name (ARN) of the target group.
     */
    @Cloud
    @Property
    private String arn;

    /**
     * The load balancer ARNs attached to this target group.
     */
    @Cloud
    @Property
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
