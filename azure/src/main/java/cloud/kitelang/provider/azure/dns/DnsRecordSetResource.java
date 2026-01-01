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
 *
 * @see <a href="https://learn.microsoft.com/en-us/azure/dns/dns-zones-records">Azure DNS Records Documentation</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("DnsRecordSet")
public class DnsRecordSetResource {

    @Property(description = "The name of the DNS zone", optional = false)
    private String zoneName;

    @Property(description = "The resource group name", optional = false)
    private String resourceGroup;

    @Property(description = "The name of the record set. Use @ for the zone apex", optional = false)
    private String name;

    @Property(description = "The DNS record type",
              validValues = {"A", "AAAA", "CNAME", "MX", "TXT", "NS", "SRV", "CAA", "PTR", "SOA"},
              optional = false)
    private String type;

    @Property(description = "The TTL (time to live) in seconds. Required unless targetResourceId is specified")
    private Long ttl;

    @Property(description = "A records (IPv4 addresses). Used when type = A")
    private List<String> aRecords;

    @Property(description = "AAAA records (IPv6 addresses). Used when type = AAAA")
    private List<String> aaaaRecords;

    @Property(description = "CNAME record (canonical name). Used when type = CNAME")
    private String cnameRecord;

    @Property(description = "MX records. Used when type = MX")
    private List<MxRecord> mxRecords;

    @Property(description = "TXT records. Used when type = TXT")
    private List<String> txtRecords;

    @Property(description = "NS records. Used when type = NS")
    private List<String> nsRecords;

    @Property(description = "SRV records. Used when type = SRV")
    private List<SrvRecord> srvRecords;

    @Property(description = "CAA records. Used when type = CAA")
    private List<CaaRecord> caaRecords;

    @Property(description = "PTR records. Used when type = PTR")
    private List<String> ptrRecords;

    @Property(description = "Target resource ID for alias record. Points to an Azure resource (e.g., Public IP, Traffic Manager)")
    private String targetResourceId;

    @Property(description = "Metadata for the record set")
    private Map<String, String> metadata;

    // --- Cloud-managed properties (read-only) ---

    @Cloud(importable = true)
    @Property(description = "The resource ID of the record set")
    private String id;

    @Cloud
    @Property(description = "The fully qualified domain name of the record set")
    private String fqdn;

    @Cloud
    @Property(description = "The provisioning state")
    private String provisioningState;

    @Cloud
    @Property(description = "The ETag of the record set")
    private String etag;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MxRecord {
        @Property(description = "The preference value (lower = higher priority)")
        private Integer preference;

        @Property(description = "The mail exchange server")
        private String exchange;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SrvRecord {
        @Property(description = "The priority value (lower = higher priority)")
        private Integer priority;

        @Property(description = "The weight for load balancing")
        private Integer weight;

        @Property(description = "The port number")
        private Integer port;

        @Property(description = "The target server hostname")
        private String target;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaaRecord {
        @Property(description = "The flags (0-255)")
        private Integer flags;

        @Property(description = "The tag",
                  validValues = {"issue", "issuewild", "iodef"})
        private String tag;

        @Property(description = "The value")
        private String value;
    }
}
