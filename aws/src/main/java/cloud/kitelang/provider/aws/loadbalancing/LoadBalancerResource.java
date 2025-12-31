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

    @Property(description = "The name of the load balancer. Must be unique within your AWS account")
    private String name;

    @Property(description = "The type: application (ALB), network (NLB), gateway (GWLB)")
    private String type;

    @Property(description = "The scheme: internet-facing or internal. Default: internet-facing")
    private String scheme;

    @Property(description = "The subnet IDs. At least two in different Availability Zones")
    private List<String> subnets;

    @Property(description = "The security group IDs. Required for ALB, not applicable for NLB")
    private List<String> securityGroups;

    @Property(description = "The IP address type: ipv4 or dualstack. Default: ipv4")
    private String ipAddressType;

    @Property(description = "Enable deletion protection. Default: false")
    private Boolean deletionProtection;

    @Property(description = "Enable cross-zone load balancing. Default: true for ALB, false for NLB")
    private Boolean crossZoneLoadBalancing;

    @Property(description = "Enable HTTP/2 support (ALB only). Default: true")
    private Boolean enableHttp2;

    @Property(description = "Idle timeout in seconds (ALB only). Default: 60")
    private Integer idleTimeout;

    @Property(description = "Enable access logs")
    private Boolean accessLogsEnabled;

    @Property(description = "S3 bucket for access logs")
    private String accessLogsBucket;

    @Property(description = "S3 bucket prefix for access logs")
    private String accessLogsPrefix;

    @Property(description = "Tags to apply to the load balancer")
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    @Cloud
    @Property(description = "The Amazon Resource Name (ARN) of the load balancer")
    private String arn;

    @Cloud
    @Property(description = "The DNS name of the load balancer")
    private String dnsName;

    @Cloud
    @Property(description = "The canonical hosted zone ID of the load balancer")
    private String canonicalHostedZoneId;

    @Cloud
    @Property(description = "The VPC ID of the load balancer")
    private String vpcId;

    @Cloud
    @Property(description = "The current state of the load balancer")
    private String state;

    @Cloud
    @Property(description = "The Availability Zones for the load balancer")
    private List<String> availabilityZones;
}
