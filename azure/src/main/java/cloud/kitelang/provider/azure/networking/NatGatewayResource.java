package cloud.kitelang.provider.azure.networking;

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
 * Azure NAT Gateway - enables outbound internet connectivity for private subnets.
 *
 * Example usage:
 * <pre>
 * resource NatGateway main {
 *     name = "main-nat"
 *     resourceGroup = main.name
 *     location = main.location
 *     idleTimeoutInMinutes = 10
 *     publicIpAddressIds = [pip.id]
 *     tags = {
 *         Environment: "production"
 *     }
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("NatGateway")
public class NatGatewayResource {

    /**
     * The name of the NAT gateway.
     * Required.
     */
    @Property
    private String name;

    /**
     * The Azure resource group name.
     * Required.
     */
    @Property
    private String resourceGroup;

    /**
     * The Azure region/location.
     * Required.
     */
    @Property
    private String location;

    /**
     * The idle timeout in minutes.
     * Default: 4, Range: 4-120
     */
    @Property
    private Integer idleTimeoutInMinutes;

    /**
     * List of public IP address resource IDs to associate.
     */
    @Property
    private List<String> publicIpAddressIds;

    /**
     * List of public IP prefix resource IDs to associate.
     */
    @Property
    private List<String> publicIpPrefixIds;

    /**
     * The SKU name: Standard.
     * Default: Standard (only option currently)
     */
    @Property
    private String skuName;

    /**
     * Tags to apply to the NAT gateway.
     */
    @Property
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    /**
     * The Azure resource ID of the NAT gateway.
     */
    @Cloud
    @Property
    private String id;

    /**
     * The provisioning state of the NAT gateway.
     */
    @Cloud
    @Property
    private String provisioningState;

    /**
     * The resource GUID of the NAT gateway.
     */
    @Cloud
    @Property
    private String resourceGuid;

    /**
     * List of subnet IDs using this NAT gateway.
     */
    @Cloud
    @Property
    private List<String> subnetIds;
}
