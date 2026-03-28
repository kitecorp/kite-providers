package cloud.kitelang.provider.gcp.networking;

import cloud.kitelang.api.annotations.Cloud;
import cloud.kitelang.api.annotations.Property;
import cloud.kitelang.api.annotations.TypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GCP VPC Network - a virtual private cloud network.
 *
 * <p>GCP VPC networks are global resources (not regional).
 * Unlike AWS VPCs, GCP VPCs do not have a CIDR block at the network level;
 * IP ranges are defined at the subnetwork level.</p>
 *
 * Example usage:
 * <pre>
 * resource VpcNetwork main {
 *     name = "main-network"
 *     autoCreateSubnetworks = false
 *     routingMode = "REGIONAL"
 *     description = "Main production network"
 * }
 * </pre>
 *
 * @see <a href="https://cloud.google.com/vpc/docs/vpc">GCP VPC Documentation</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("VpcNetwork")
public class VpcNetworkResource {

    @Property(description = "The name of the network", optional = false)
    private String name;

    @Property(description = "Whether to automatically create subnetworks in each region")
    private Boolean autoCreateSubnetworks = true;

    @Property(description = "The network-wide routing mode",
              validValues = {"REGIONAL", "GLOBAL"})
    private String routingMode = "REGIONAL";

    @Property(description = "An optional description of the network")
    private String description;

    // --- Cloud-managed properties (read-only) ---

    @Cloud(importable = true)
    @Property(description = "The unique network ID assigned by GCP")
    private String networkId;

    @Cloud
    @Property(description = "The self-link URL of the network")
    private String selfLink;

    @Cloud
    @Property(description = "The creation timestamp of the network")
    private String creationTimestamp;
}
