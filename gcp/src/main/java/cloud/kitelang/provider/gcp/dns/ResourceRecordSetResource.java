package cloud.kitelang.provider.gcp.dns;

import cloud.kitelang.api.annotations.Property;
import cloud.kitelang.api.annotations.TypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * GCP Cloud DNS Resource Record Set - a DNS record within a managed zone.
 *
 * Example usage:
 * <pre>
 * resource ResourceRecordSet www {
 *     managedZone = "main-zone"
 *     name = "www.example.com."
 *     type = "A"
 *     ttl = 300
 *     rrdatas = ["192.0.2.1", "192.0.2.2"]
 * }
 *
 * resource ResourceRecordSet mail {
 *     managedZone = "main-zone"
 *     name = "example.com."
 *     type = "MX"
 *     ttl = 300
 *     rrdatas = ["10 mail.example.com."]
 * }
 * </pre>
 *
 * @see <a href="https://cloud.google.com/dns/docs/records">GCP Cloud DNS Records Documentation</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("ResourceRecordSet")
public class ResourceRecordSetResource {

    @Property(description = "The managed zone name this record belongs to", optional = false)
    private String managedZone;

    @Property(description = "The fully qualified domain name (must end with a dot)", optional = false)
    private String name;

    @Property(description = "The DNS record type",
              validValues = {"A", "AAAA", "CNAME", "MX", "TXT", "NS", "SOA", "SRV", "CAA", "PTR", "SPF"})
    private String type;

    @Property(description = "The TTL (time to live) in seconds")
    private Integer ttl;

    @Property(description = "The record data values")
    private List<String> rrdatas;
}
