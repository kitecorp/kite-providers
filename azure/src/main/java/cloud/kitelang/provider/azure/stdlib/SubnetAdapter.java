package cloud.kitelang.provider.azure.stdlib;

import cloud.kitelang.provider.ResourceTypeHandler;
import cloud.kitelang.provider.StandardTypeAdapter;
import cloud.kitelang.provider.azure.networking.SubnetResource;
import cloud.kitelang.provider.azure.networking.SubnetResourceType;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapts the standard library {@code Subnet} type to Azure {@code Subnet}.
 *
 * <p>Property mapping:</p>
 * <ul>
 *   <li>{@code networkId} -> {@code vnetName} (Azure uses the VNet name, not ID, for subnet creation).
 *       If the value looks like an Azure resource ID, the VNet name is extracted from it.</li>
 *   <li>{@code cidrBlock} -> {@code addressPrefix}</li>
 *   <li>{@code name} -> {@code name}</li>
 * </ul>
 *
 * <p>Note: {@code availabilityZone} is not mapped because Azure subnets are not AZ-scoped.</p>
 *
 * <p>Cloud-managed properties mapped back:</p>
 * <ul>
 *   <li>{@code id} <- {@code id}</li>
 *   <li>{@code state} <- {@code provisioningState}</li>
 * </ul>
 */
public class SubnetAdapter implements StandardTypeAdapter<SubnetResource> {

    private final SubnetResourceType handler;

    public SubnetAdapter(SubnetResourceType handler) {
        this.handler = handler;
    }

    @Override
    public String standardTypeName() {
        return "Subnet";
    }

    @Override
    public String concreteTypeName() {
        return "Subnet";
    }

    @Override
    @SuppressWarnings("unchecked")
    public SubnetResource toConcreteProperties(Map<String, Object> abstractProps) {
        var builder = SubnetResource.builder();

        // Map name -> name
        var name = (String) abstractProps.get("name");
        if (name != null) {
            builder.name(name);
        }

        // Map networkId -> vnetName
        // The abstract networkId could be an Azure resource ID or a simple VNet name.
        // If it's a full resource ID, extract the VNet name from it.
        var networkId = (String) abstractProps.get("networkId");
        if (networkId != null) {
            builder.vnetName(extractVnetName(networkId));
        }

        // Map cidrBlock -> addressPrefix
        var cidrBlock = (String) abstractProps.get("cidrBlock");
        if (cidrBlock != null) {
            builder.addressPrefix(cidrBlock);
        }

        return builder.build();
    }

    @Override
    public Map<String, Object> toAbstractProperties(SubnetResource concrete) {
        var result = new HashMap<String, Object>();

        if (concrete.getId() != null) {
            result.put("id", concrete.getId());
        }
        if (concrete.getProvisioningState() != null) {
            result.put("state", concrete.getProvisioningState());
        }

        return result;
    }

    @Override
    public ResourceTypeHandler<SubnetResource> getConcreteHandler() {
        return handler;
    }

    /**
     * Extract the VNet name from a value that may be an Azure resource ID or a simple name.
     * Azure VNet resource IDs follow the format:
     * {@code /subscriptions/.../resourceGroups/.../providers/Microsoft.Network/virtualNetworks/{vnetName}}
     *
     * @param networkIdOrName either a full Azure resource ID or a simple VNet name
     * @return the VNet name
     */
    static String extractVnetName(String networkIdOrName) {
        if (networkIdOrName.contains("/virtualNetworks/")) {
            var parts = networkIdOrName.split("/virtualNetworks/");
            if (parts.length > 1) {
                // The vnet name may be followed by /subnets/..., strip that
                var vnetPart = parts[1];
                var slashIndex = vnetPart.indexOf('/');
                return slashIndex >= 0 ? vnetPart.substring(0, slashIndex) : vnetPart;
            }
        }
        return networkIdOrName;
    }
}
