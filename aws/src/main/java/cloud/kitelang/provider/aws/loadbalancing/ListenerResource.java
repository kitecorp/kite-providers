package cloud.kitelang.provider.aws.loadbalancing;

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
 *
 * @see <a href="https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-listeners.html">AWS Listener Documentation</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("Listener")
public class ListenerResource {

    @Property(description = "The ARN of the load balancer")
    private String loadBalancerArn;

    @Property(description = "The port on which the listener listens")
    private Integer port;

    @Property(description = "The protocol for the listener",
              validValues = {"HTTP", "HTTPS", "TCP", "TLS", "UDP", "TCP_UDP", "GENEVE"})
    private String protocol;

    @Property(description = "The ARN of the default SSL server certificate (HTTPS/TLS only)")
    private String certificateArn;

    @Property(description = "The SSL policy for HTTPS/TLS listeners")
    private String sslPolicy;

    @Property(description = "The ALPN policy for TLS listeners (NLB only)")
    private String alpnPolicy;

    @Property(description = "The ARN of the default target group for forward action")
    private String defaultTargetGroupArn;

    @Property(description = "The default action type",
              validValues = {"forward", "redirect", "fixed-response"})
    private String defaultActionType = "forward";

    @Property(description = "Redirect configuration when defaultActionType is 'redirect'")
    private RedirectConfig redirectConfig;

    @Property(description = "Fixed response configuration when defaultActionType is 'fixed-response'")
    private FixedResponseConfig fixedResponseConfig;

    @Property(description = "Tags to apply to the listener")
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    @Cloud(importable = true)
    @Property(description = "The ARN of the listener")
    private String arn;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RedirectConfig {
        @Property(description = "The protocol (HTTP or HTTPS)",
                  validValues = {"HTTP", "HTTPS"})
        private String protocol = "#{protocol}";

        @Property(description = "The hostname")
        private String host = "#{host}";

        @Property(description = "The port")
        private String port = "#{port}";

        @Property(description = "The path")
        private String path = "/#{path}";

        @Property(description = "The query string")
        private String query = "#{query}";

        @Property(description = "The HTTP redirect code",
                  validValues = {"HTTP_301", "HTTP_302"},
                  optional = false)
        private String statusCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FixedResponseConfig {
        @Property(description = "The HTTP response code", optional = false)
        private String statusCode;

        @Property(description = "The content type",
                  validValues = {"text/plain", "text/css", "text/html", "application/javascript", "application/json"})
        private String contentType;

        @Property(description = "The message body")
        private String messageBody;
    }
}
