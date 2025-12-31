package cloud.kitelang.provider.azure.dns;

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
 * Azure DNS Record Set resource.
 *
 * Example usage:
 * <pre>
 * // A record
 * resource DnsRecordSet www {
 *     zoneName = zone.name
 *     resourceGroup = rg.name
 *     name = "www"
 *     type = "A"
 *     ttl = 300
 *     aRecords = ["192.0.2.1", "192.0.2.2"]
 * }
 *
 * // CNAME record
 * resource DnsRecordSet api {
 *     zoneName = zone.name
 *     resourceGroup = rg.name
 *     name = "api"
 *     type = "CNAME"
 *     ttl = 300
 *     cnameRecord = "api.backend.example.com"
 * }
 *
 * // MX record
 * resource DnsRecordSet mail {
 *     zoneName = zone.name
 *     resourceGroup = rg.name
 *     name = "@"
 *     type = "MX"
 *     ttl = 300
 *     mxRecords = [
 *         { preference: 10, exchange: "mail1.example.com" },
 *         { preference: 20, exchange: "mail2.example.com" }
 *     ]
 * }
 *
 * // TXT record
 * resource DnsRecordSet spf {
 *     zoneName = zone.name
 *     resourceGroup = rg.name
 *     name = "@"
 *     type = "TXT"
 *     ttl = 300
 *     txtRecords = ["v=spf1 include:_spf.google.com ~all"]
 * }
 *
 * // Alias to Azure resource
 * resource DnsRecordSet app {
 *     zoneName = zone.name
 *     resourceGroup = rg.name
 *     name = "app"
 *     type = "A"
 *     targetResourceId = pip.id
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("DnsRecordSet")
public class DnsRecordSetResource {

    /**
     * The name of the DNS zone.
     * Required.
     */
    @Property
    private String zoneName;

    /**
     * The resource group name.
     * Required.
     */
    @Property
    private String resourceGroup;

    /**
     * The name of the record set.
     * Use "@" for the zone apex.
     * Required.
     */
    @Property
    private String name;

    /**
     * The DNS record type.
     * Valid values: A, AAAA, CNAME, MX, TXT, NS, SRV, CAA, PTR, SOA.
     * Required.
     */
    @Property
    private String type;

    /**
     * The TTL (time to live) in seconds.
     * Required unless targetResourceId is specified.
     */
    @Property
    private Long ttl;

    /**
     * A records (IPv4 addresses).
     * Used when type = "A".
     */
    @Property
    private List<String> aRecords;

    /**
     * AAAA records (IPv6 addresses).
     * Used when type = "AAAA".
     */
    @Property
    private List<String> aaaaRecords;

    /**
     * CNAME record (canonical name).
     * Used when type = "CNAME".
     */
    @Property
    private String cnameRecord;

    /**
     * MX records.
     * Used when type = "MX".
     */
    @Property
    private List<MxRecord> mxRecords;

    /**
     * TXT records.
     * Used when type = "TXT".
     */
    @Property
    private List<String> txtRecords;

    /**
     * NS records.
     * Used when type = "NS".
     */
    @Property
    private List<String> nsRecords;

    /**
     * SRV records.
     * Used when type = "SRV".
     */
    @Property
    private List<SrvRecord> srvRecords;

    /**
     * CAA records.
     * Used when type = "CAA".
     */
    @Property
    private List<CaaRecord> caaRecords;

    /**
     * PTR records.
     * Used when type = "PTR".
     */
    @Property
    private List<String> ptrRecords;

    /**
     * Target resource ID for alias record.
     * Points to an Azure resource (e.g., Public IP, Traffic Manager).
     */
    @Property
    private String targetResourceId;

    /**
     * Metadata for the record set.
     */
    @Property
    private Map<String, String> metadata;

    // --- Cloud-managed properties (read-only) ---

    /**
     * The resource ID of the record set.
     */
    @Cloud
    @Property
    private String id;

    /**
     * The fully qualified domain name of the record set.
     */
    @Cloud
    @Property
    private String fqdn;

    /**
     * The provisioning state.
     */
    @Cloud
    @Property
    private String provisioningState;

    /**
     * The ETag of the record set.
     */
    @Cloud
    @Property
    private String etag;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MxRecord {
        /**
         * The preference value (lower = higher priority).
         */
        private Integer preference;

        /**
         * The mail exchange server.
         */
        private String exchange;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SrvRecord {
        /**
         * The priority value (lower = higher priority).
         */
        private Integer priority;

        /**
         * The weight for load balancing.
         */
        private Integer weight;

        /**
         * The port number.
         */
        private Integer port;

        /**
         * The target server hostname.
         */
        private String target;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaaRecord {
        /**
         * The flags (0-255).
         */
        private Integer flags;

        /**
         * The tag (e.g., "issue", "issuewild", "iodef").
         */
        private String tag;

        /**
         * The value.
         */
        private String value;
    }
}
