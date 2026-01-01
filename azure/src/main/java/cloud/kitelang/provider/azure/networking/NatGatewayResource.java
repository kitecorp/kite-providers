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
 *
 * @see <a href="https://learn.microsoft.com/en-us/azure/nat-gateway/nat-overview">Azure NAT Gateway Documentation</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("NatGateway")
public class NatGatewayResource {

    @Property(description = "The name of the NAT gateway", optional = false)
    private String name;

    @Property(description = "The Azure resource group name", optional = false)
    private String resourceGroup;

    @Property(description = "The Azure region/location", optional = false)
    private String location;

    @Property(description = "The idle timeout in minutes. Range: 4-120")
    private Integer idleTimeoutInMinutes = 4;

    @Property(description = "List of public IP address resource IDs to associate")
    private List<String> publicIpAddressIds;

    @Property(description = "List of public IP prefix resource IDs to associate")
    private List<String> publicIpPrefixIds;

    @Property(description = "The SKU name",
              validValues = {"Standard"})
    private String skuName = "Standard";

    @Property(description = "Tags to apply to the NAT gateway")
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    @Cloud(importable = true)
    @Property(description = "The Azure resource ID of the NAT gateway")
    private String id;

    @Cloud
    @Property(description = "The provisioning state of the NAT gateway")
    private String provisioningState;

    @Cloud
    @Property(description = "The resource GUID of the NAT gateway")
    private String resourceGuid;

    @Cloud
    @Property(description = "List of subnet IDs using this NAT gateway")
    private List<String> subnetIds;
}
