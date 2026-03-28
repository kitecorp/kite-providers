package cloud.kitelang.provider.gcp.networking;

import cloud.kitelang.api.annotations.Cloud;
import cloud.kitelang.api.annotations.Property;
import cloud.kitelang.api.annotations.TypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GCP Subnetwork - a regional subdivision of a VPC network.
 *
 * Example usage:
 * <pre>
 * resource Subnetwork web {
 *     name = "web-subnet"
 *     network = network.selfLink
 *     ipCidrRange = "10.0.1.0/24"
 *     region = "us-central1"
 *     privateIpGoogleAccess = true
 * }
 * </pre>
 *
 * @see <a href="https://cloud.google.com/vpc/docs/subnets">GCP Subnet Documentation</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("Subnetwork")
public class SubnetworkResource {

    @Property(description = "The name of the subnetwork", optional = false)
    private String name;

    @Property(description = "The VPC network name or self-link this subnet belongs to", optional = false)
    private String network;

    @Property(description = "The IPv4 CIDR range for this subnetwork (e.g., 10.0.1.0/24)", optional = false)
    private String ipCidrRange;

    @Property(description = "The GCP region for this subnetwork (e.g., us-central1)", optional = false)
    private String region;

    @Property(description = "Whether VMs in this subnet can access Google APIs without external IP")
    private Boolean privateIpGoogleAccess = false;

    @Property(description = "An optional description of the subnetwork")
    private String description;

    // --- Cloud-managed properties (read-only) ---

    @Cloud(importable = true)
    @Property(description = "The unique subnetwork ID assigned by GCP")
    private String subnetworkId;

    @Cloud
    @Property(description = "The self-link URL of the subnetwork")
    private String selfLink;

    @Cloud
    @Property(description = "The gateway address for default routing in this subnetwork")
    private String gatewayAddress;
}
