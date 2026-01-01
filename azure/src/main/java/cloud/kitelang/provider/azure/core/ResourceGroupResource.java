package cloud.kitelang.provider.azure.core;

import cloud.kitelang.api.annotations.Cloud;
import cloud.kitelang.api.annotations.Property;
import cloud.kitelang.api.annotations.TypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Azure Resource Group.
 * A container that holds related resources for an Azure solution.
 *
 * Example usage:
 * <pre>
 * resource ResourceGroup main {
 *     name = "my-resource-group"
 *     location = "eastus"
 *     tags = {
 *         Environment: "production"
 *         ManagedBy: "kite"
 *     }
 * }
 * </pre>
 *
 * @see <a href="https://learn.microsoft.com/en-us/azure/azure-resource-manager/management/manage-resource-groups-portal">Azure Resource Group Documentation</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("ResourceGroup")
public class ResourceGroupResource {

    @Property(description = "The name of the resource group. Must be unique within the subscription", optional = false)
    private String name;

    @Property(description = "The Azure region/location (e.g., eastus, westeurope)", optional = false)
    private String location;

    @Property(description = "Tags to apply to the resource group")
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    @Cloud(importable = true)
    @Property(description = "The Azure resource ID of the resource group")
    private String id;

    @Cloud
    @Property(description = "The provisioning state of the resource group")
    private String provisioningState;
}
