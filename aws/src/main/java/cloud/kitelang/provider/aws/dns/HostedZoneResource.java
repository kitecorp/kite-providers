package cloud.kitelang.provider.aws.dns;

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
 * AWS Route53 Hosted Zone resource.
 *
 * Example usage:
 * <pre>
 * resource HostedZone main {
 *     name = "example.com"
 *     comment = "Main domain"
 *     tags = {
 *         Environment: "production"
 *     }
 * }
 *
 * resource HostedZone private {
 *     name = "internal.example.com"
 *     privateZone = true
 *     vpcId = vpc.vpcId
 *     vpcRegion = "us-east-1"
 *     comment = "Private internal zone"
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("HostedZone")
public class HostedZoneResource {

    @Property(description = "The domain name for the hosted zone (e.g., 'example.com')")
    private String name;

    @Property(description = "A comment for the hosted zone")
    private String comment;

    @Property(description = "Whether this is a private hosted zone. Default: false")
    private Boolean privateZone;

    @Property(description = "VPC ID to associate with private hosted zone")
    private String vpcId;

    @Property(description = "VPC region for private hosted zone")
    private String vpcRegion;

    @Property(description = "Tags to apply to the hosted zone")
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    @Cloud
    @Property(description = "The hosted zone ID (without /hostedzone/ prefix)")
    private String hostedZoneId;

    @Cloud
    @Property(description = "The name servers for the hosted zone")
    private List<String> nameServers;

    @Cloud
    @Property(description = "The number of resource record sets in the hosted zone")
    private Long resourceRecordSetCount;

    @Cloud
    @Property(description = "A unique string that identifies the request to create the zone")
    private String callerReference;

    @Cloud(importable = true)
    @Property(description = "The Amazon Resource Name (ARN) of the hosted zone")
    private String arn;
}
