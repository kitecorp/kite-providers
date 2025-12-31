package cloud.kitelang.provider.aws.networking;

import cloud.kitelang.api.annotations.Cloud;
import cloud.kitelang.api.annotations.Property;
import cloud.kitelang.api.annotations.TypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * AWS Elastic IP - a static public IPv4 address for dynamic cloud computing.
 *
 * Example usage:
 * <pre>
 * resource ElasticIp nat {
 *     domain = "vpc"
 *     tags = {
 *         Name: "nat-eip",
 *         Environment: "production"
 *     }
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("ElasticIp")
public class ElasticIpResource {

    @Property(description = "The domain: 'vpc' or 'standard'. Default: 'vpc'")
    private String domain;

    @Property(description = "The ID of the network border group for Local/Wavelength Zones")
    private String networkBorderGroup;

    @Property(description = "The ID of an address pool for BYOIP")
    private String publicIpv4Pool;

    @Property(description = "Tags to apply to the Elastic IP")
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    @Cloud
    @Property(description = "The allocation ID for the Elastic IP")
    private String allocationId;

    @Cloud
    @Property(description = "The Elastic IP address")
    private String publicIp;

    @Cloud
    @Property(description = "The ID of the associated instance (if any)")
    private String instanceId;

    @Cloud
    @Property(description = "The association ID (if associated)")
    private String associationId;

    @Cloud
    @Property(description = "The ID of the network interface (if associated)")
    private String networkInterfaceId;

    @Cloud
    @Property(description = "The private IP address associated with the Elastic IP")
    private String privateIpAddress;
}
