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

    /**
     * The domain name for the hosted zone.
     * Must be a fully qualified domain name (e.g., "example.com").
     * Required.
     */
    @Property
    private String name;

    /**
     * A comment for the hosted zone.
     */
    @Property
    private String comment;

    /**
     * Whether this is a private hosted zone.
     * Default: false (public zone)
     */
    @Property
    private Boolean privateZone;

    /**
     * VPC ID to associate with private hosted zone.
     * Required for private zones.
     */
    @Property
    private String vpcId;

    /**
     * VPC region for private hosted zone.
     * Required for private zones.
     */
    @Property
    private String vpcRegion;

    /**
     * Tags to apply to the hosted zone.
     */
    @Property
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    /**
     * The hosted zone ID (without /hostedzone/ prefix).
     */
    @Cloud
    @Property
    private String hostedZoneId;

    /**
     * The name servers for the hosted zone.
     */
    @Cloud
    @Property
    private List<String> nameServers;

    /**
     * The number of resource record sets in the hosted zone.
     */
    @Cloud
    @Property
    private Long resourceRecordSetCount;

    /**
     * A unique string that identifies the request to create the hosted zone.
     */
    @Cloud
    @Property
    private String callerReference;
}
