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
 * AWS Load Balancer resource (ALB/NLB).
 *
 * Example usage:
 * <pre>
 * resource LoadBalancer web {
 *     name = "web-alb"
 *     type = "application"
 *     scheme = "internet-facing"
 *     subnets = [subnet1.subnetId, subnet2.subnetId]
 *     securityGroups = [sg.securityGroupId]
 *     tags = {
 *         Name: "web-alb",
 *         Environment: "production"
 *     }
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("LoadBalancer")
public class LoadBalancerResource {

    /**
     * The name of the load balancer.
     * Required. Must be unique within your AWS account.
     */
    @Property
    private String name;

    /**
     * The type of load balancer.
     * Valid values: application (ALB), network (NLB), gateway (GWLB).
     * Default: application
     */
    @Property
    private String type;

    /**
     * The scheme for the load balancer.
     * Valid values: internet-facing, internal.
     * Default: internet-facing
     */
    @Property
    private String scheme;

    /**
     * The IDs of the subnets to attach to the load balancer.
     * Required. At least two subnets in different Availability Zones.
     */
    @Property
    private List<String> subnets;

    /**
     * The IDs of the security groups for the load balancer.
     * Required for ALB. Not applicable for NLB.
     */
    @Property
    private List<String> securityGroups;

    /**
     * The IP address type.
     * Valid values: ipv4, dualstack.
     * Default: ipv4
     */
    @Property
    private String ipAddressType;

    /**
     * Enable deletion protection.
     * Default: false
     */
    @Property
    private Boolean deletionProtection;

    /**
     * Enable cross-zone load balancing.
     * Default: true for ALB, false for NLB
     */
    @Property
    private Boolean crossZoneLoadBalancing;

    /**
     * Enable HTTP/2 support (ALB only).
     * Default: true
     */
    @Property
    private Boolean enableHttp2;

    /**
     * Idle timeout in seconds (ALB only).
     * Default: 60
     */
    @Property
    private Integer idleTimeout;

    /**
     * Enable access logs.
     */
    @Property
    private Boolean accessLogsEnabled;

    /**
     * S3 bucket for access logs.
     */
    @Property
    private String accessLogsBucket;

    /**
     * S3 bucket prefix for access logs.
     */
    @Property
    private String accessLogsPrefix;

    /**
     * Tags to apply to the load balancer.
     */
    @Property
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    /**
     * The Amazon Resource Name (ARN) of the load balancer.
     */
    @Cloud
    @Property
    private String arn;

    /**
     * The DNS name of the load balancer.
     */
    @Cloud
    @Property
    private String dnsName;

    /**
     * The canonical hosted zone ID of the load balancer.
     */
    @Cloud
    @Property
    private String canonicalHostedZoneId;

    /**
     * The VPC ID of the load balancer.
     */
    @Cloud
    @Property
    private String vpcId;

    /**
     * The current state of the load balancer.
     */
    @Cloud
    @Property
    private String state;

    /**
     * The Availability Zones for the load balancer.
     */
    @Cloud
    @Property
    private List<String> availabilityZones;
}
