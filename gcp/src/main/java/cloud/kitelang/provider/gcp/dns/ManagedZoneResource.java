package cloud.kitelang.provider.gcp.dns;

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
 * GCP Cloud DNS Managed Zone - a DNS zone hosted by Cloud DNS.
 *
 * Example usage:
 * <pre>
 * resource ManagedZone main {
 *     name = "main-zone"
 *     dnsName = "example.com."
 *     description = "Main DNS zone"
 *     labels = {
 *         environment: "production"
 *     }
 * }
 * </pre>
 *
 * @see <a href="https://cloud.google.com/dns/docs/overview">GCP Cloud DNS Documentation</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("ManagedZone")
public class ManagedZoneResource {

    @Property(description = "The name of the managed zone (must be unique within the project)", optional = false)
    private String name;

    @Property(description = "The DNS name of the zone (must end with a dot, e.g., 'example.com.')", optional = false)
    private String dnsName;

    @Property(description = "A description for the managed zone")
    private String description;

    @Property(description = "Labels to apply to the managed zone")
    private Map<String, String> labels;

    // --- Cloud-managed properties (read-only) ---

    @Cloud(importable = true)
    @Property(description = "The unique managed zone ID assigned by GCP")
    private String managedZoneId;

    @Cloud
    @Property(description = "The name servers assigned to this managed zone")
    private List<String> nameServers;

    @Cloud
    @Property(description = "The creation time of the managed zone")
    private String creationTime;
}
