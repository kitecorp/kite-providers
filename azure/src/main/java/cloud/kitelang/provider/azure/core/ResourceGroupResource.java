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
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("ResourceGroup")
public class ResourceGroupResource {

    /**
     * The name of the resource group.
     * Required. Must be unique within the subscription.
     */
    @Property
    private String name;

    /**
     * The Azure region/location (e.g., "eastus", "westeurope").
     * Required.
     */
    @Property
    private String location;

    /**
     * Tags to apply to the resource group.
     */
    @Property
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    /**
     * The Azure resource ID of the resource group.
     */
    @Cloud
    @Property
    private String id;

    /**
     * The provisioning state of the resource group.
     */
    @Cloud
    @Property
    private String provisioningState;
}
