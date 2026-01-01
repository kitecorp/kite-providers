package cloud.kitelang.provider.aws.dns;

import cloud.kitelang.api.annotations.Property;
import cloud.kitelang.api.annotations.TypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AWS Route53 Record Set resource.
 *
 * Example usage:
 * <pre>
 * // A record
 * resource RecordSet www {
 *     hostedZoneId = zone.hostedZoneId
 *     name = "www.example.com"
 *     type = "A"
 *     ttl = 300
 *     records = ["192.0.2.1", "192.0.2.2"]
 * }
 *
 * // CNAME record
 * resource RecordSet api {
 *     hostedZoneId = zone.hostedZoneId
 *     name = "api.example.com"
 *     type = "CNAME"
 *     ttl = 300
 *     records = ["api.backend.example.com"]
 * }
 *
 * // Alias to ALB
 * resource RecordSet app {
 *     hostedZoneId = zone.hostedZoneId
 *     name = "app.example.com"
 *     type = "A"
 *     aliasTarget = {
 *         hostedZoneId: lb.canonicalHostedZoneId,
 *         dnsName: lb.dnsName,
 *         evaluateTargetHealth: true
 *     }
 * }
 *
 * // MX record
 * resource RecordSet mail {
 *     hostedZoneId = zone.hostedZoneId
 *     name = "example.com"
 *     type = "MX"
 *     ttl = 300
 *     records = ["10 mail1.example.com", "20 mail2.example.com"]
 * }
 *
 * // TXT record
 * resource RecordSet spf {
 *     hostedZoneId = zone.hostedZoneId
 *     name = "example.com"
 *     type = "TXT"
 *     ttl = 300
 *     records = ["\"v=spf1 include:_spf.google.com ~all\""]
 * }
 * </pre>
 *
 * @see <a href="https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/rrsets-working-with.html">AWS Route53 Record Set Documentation</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("RecordSet")
public class RecordSetResource {

    @Property(description = "The ID of the hosted zone")
    private String hostedZoneId;

    @Property(description = "The name of the record (fully qualified domain name)")
    private String name;

    @Property(description = "The DNS record type",
              validValues = {"A", "AAAA", "CNAME", "MX", "TXT", "NS", "SOA", "SRV", "CAA", "PTR"})
    private String type;

    @Property(description = "The TTL (time to live) in seconds")
    private Long ttl;

    @Property(description = "The record values")
    private List<String> records;

    @Property(description = "Alias target for AWS resources (ELB, CloudFront, S3, etc.)")
    private AliasTarget aliasTarget;

    @Property(description = "Weighted routing weight (0-255)")
    private Long weight;

    @Property(description = "Set identifier for routing policies")
    private String setIdentifier;

    @Property(description = "Failover routing type",
              validValues = {"PRIMARY", "SECONDARY"})
    private String failover;

    @Property(description = "Geolocation routing configuration")
    private GeoLocation geoLocation;

    @Property(description = "Health check ID to associate with the record")
    private String healthCheckId;

    @Property(description = "Whether this is a multivalue answer record")
    private Boolean multiValueAnswer;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AliasTarget {
        @Property(description = "The hosted zone ID of the alias target", optional = false)
        private String hostedZoneId;

        @Property(description = "The DNS name of the alias target", optional = false)
        private String dnsName;

        @Property(description = "Whether to evaluate target health")
        private Boolean evaluateTargetHealth = false;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeoLocation {
        @Property(description = "The continent code",
                  validValues = {"AF", "AN", "AS", "EU", "OC", "NA", "SA"})
        private String continentCode;

        @Property(description = "The country code (ISO 3166-1 alpha-2)")
        private String countryCode;

        @Property(description = "The subdivision code (e.g., US state code)")
        private String subdivisionCode;
    }
}
