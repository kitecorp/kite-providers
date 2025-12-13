package cloud.kitelang.provider.aws;

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

    /**
     * The domain: "vpc" or "standard" (EC2-Classic, deprecated).
     * Default: "vpc"
     */
    @Property
    private String domain;

    /**
     * The ID of the network border group.
     * For Local Zones or Wavelength Zones.
     */
    @Property
    private String networkBorderGroup;

    /**
     * The ID of an address pool for BYOIP.
     */
    @Property
    private String publicIpv4Pool;

    /**
     * Tags to apply to the Elastic IP.
     */
    @Property
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    /**
     * The allocation ID for the Elastic IP.
     * Use this when associating with NAT Gateway.
     */
    @Cloud
    @Property
    private String allocationId;

    /**
     * The Elastic IP address.
     */
    @Cloud
    @Property
    private String publicIp;

    /**
     * The ID of the associated instance (if any).
     */
    @Cloud
    @Property
    private String instanceId;

    /**
     * The association ID (if associated).
     */
    @Cloud
    @Property
    private String associationId;

    /**
     * The ID of the network interface (if associated).
     */
    @Cloud
    @Property
    private String networkInterfaceId;

    /**
     * The private IP address associated with the Elastic IP.
     */
    @Cloud
    @Property
    private String privateIpAddress;
}
