package cloud.kitelang.provider.aws;

import cloud.kitelang.api.annotations.Cloud;
import cloud.kitelang.api.annotations.Property;
import cloud.kitelang.api.annotations.TypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * AWS Load Balancer Listener resource.
 *
 * Example usage:
 * <pre>
 * resource Listener http {
 *     loadBalancerArn = lb.arn
 *     port = 80
 *     protocol = "HTTP"
 *     defaultTargetGroupArn = tg.arn
 * }
 *
 * resource Listener https {
 *     loadBalancerArn = lb.arn
 *     port = 443
 *     protocol = "HTTPS"
 *     certificateArn = "arn:aws:acm:..."
 *     sslPolicy = "ELBSecurityPolicy-TLS13-1-2-2021-06"
 *     defaultTargetGroupArn = tg.arn
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("Listener")
public class ListenerResource {

    /**
     * The ARN of the load balancer.
     * Required.
     */
    @Property
    private String loadBalancerArn;

    /**
     * The port on which the listener listens.
     * Required.
     */
    @Property
    private Integer port;

    /**
     * The protocol for connections from clients to the load balancer.
     * Valid values: HTTP, HTTPS, TCP, TLS, UDP, TCP_UDP, GENEVE.
     * Required.
     */
    @Property
    private String protocol;

    /**
     * The ARN of the default SSL server certificate (HTTPS/TLS only).
     */
    @Property
    private String certificateArn;

    /**
     * The SSL policy for HTTPS/TLS listeners.
     * Example: ELBSecurityPolicy-TLS13-1-2-2021-06
     */
    @Property
    private String sslPolicy;

    /**
     * The ALPN policy for TLS listeners (NLB only).
     * Valid values: HTTP1Only, HTTP2Only, HTTP2Optional, HTTP2Preferred, None.
     */
    @Property
    private String alpnPolicy;

    /**
     * The ARN of the default target group for the default action.
     * Required when defaultActionType is "forward".
     */
    @Property
    private String defaultTargetGroupArn;

    /**
     * The default action type.
     * Valid values: forward, redirect, fixed-response.
     * Default: forward
     */
    @Property
    private String defaultActionType;

    /**
     * Redirect configuration (when defaultActionType is "redirect").
     */
    @Property
    private RedirectConfig redirectConfig;

    /**
     * Fixed response configuration (when defaultActionType is "fixed-response").
     */
    @Property
    private FixedResponseConfig fixedResponseConfig;

    /**
     * Tags to apply to the listener.
     */
    @Property
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    /**
     * The ARN of the listener.
     */
    @Cloud
    @Property
    private String arn;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RedirectConfig {
        /**
         * The protocol (HTTP or HTTPS).
         * Default: #{protocol}
         */
        private String protocol;

        /**
         * The hostname.
         * Default: #{host}
         */
        private String host;

        /**
         * The port.
         * Default: #{port}
         */
        private String port;

        /**
         * The path.
         * Default: /#{path}
         */
        private String path;

        /**
         * The query string.
         * Default: #{query}
         */
        private String query;

        /**
         * The HTTP redirect code.
         * Valid values: HTTP_301, HTTP_302.
         * Required.
         */
        private String statusCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FixedResponseConfig {
        /**
         * The HTTP response code.
         * Required.
         */
        private String statusCode;

        /**
         * The content type.
         * Valid values: text/plain, text/css, text/html, application/javascript, application/json.
         */
        private String contentType;

        /**
         * The message body.
         */
        private String messageBody;
    }
}
