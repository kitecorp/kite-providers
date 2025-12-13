package cloud.kitelang.provider.aws;

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
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("RecordSet")
public class RecordSetResource {

    /**
     * The ID of the hosted zone.
     * Required.
     */
    @Property
    private String hostedZoneId;

    /**
     * The name of the record (fully qualified domain name).
     * Required.
     */
    @Property
    private String name;

    /**
     * The DNS record type.
     * Valid values: A, AAAA, CNAME, MX, TXT, NS, SOA, SRV, CAA, PTR.
     * Required.
     */
    @Property
    private String type;

    /**
     * The TTL (time to live) in seconds.
     * Required unless aliasTarget is specified.
     */
    @Property
    private Long ttl;

    /**
     * The record values.
     * Required unless aliasTarget is specified.
     */
    @Property
    private List<String> records;

    /**
     * Alias target for AWS resources (ELB, CloudFront, S3, etc.).
     * Use instead of ttl/records for alias records.
     */
    @Property
    private AliasTarget aliasTarget;

    /**
     * Weighted routing weight (0-255).
     * Required for weighted routing.
     */
    @Property
    private Long weight;

    /**
     * Set identifier for routing policies.
     * Required for weighted, latency, geolocation, failover, and multivalue routing.
     */
    @Property
    private String setIdentifier;

    /**
     * Failover routing type.
     * Valid values: PRIMARY, SECONDARY.
     */
    @Property
    private String failover;

    /**
     * Geolocation routing configuration.
     */
    @Property
    private GeoLocation geoLocation;

    /**
     * Health check ID to associate with the record.
     */
    @Property
    private String healthCheckId;

    /**
     * Whether this is a multivalue answer record.
     */
    @Property
    private Boolean multiValueAnswer;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AliasTarget {
        /**
         * The hosted zone ID of the alias target.
         * Required.
         */
        private String hostedZoneId;

        /**
         * The DNS name of the alias target.
         * Required.
         */
        private String dnsName;

        /**
         * Whether to evaluate target health.
         * Default: false
         */
        private Boolean evaluateTargetHealth;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeoLocation {
        /**
         * The continent code.
         * Valid values: AF, AN, AS, EU, OC, NA, SA.
         */
        private String continentCode;

        /**
         * The country code (ISO 3166-1 alpha-2).
         */
        private String countryCode;

        /**
         * The subdivision code (e.g., US state code).
         */
        private String subdivisionCode;
    }
}
