package cloud.kitelang.provider.azure.stdlib;

import cloud.kitelang.provider.ResourceTypeHandler;
import cloud.kitelang.provider.StandardTypeAdapter;
import cloud.kitelang.provider.azure.networking.VnetResource;
import cloud.kitelang.provider.azure.networking.VnetResourceType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapts the standard library {@code Network} type to Azure {@code Vnet}.
 *
 * <p>Property mapping:</p>
 * <ul>
 *   <li>{@code cidrBlock} -> {@code addressSpaces} (wrapped as single-element list)</li>
 *   <li>{@code name} -> {@code name}</li>
 *   <li>{@code tags} -> {@code tags}</li>
 * </ul>
 *
 * <p>Note: {@code enableDnsSupport} and {@code enableDnsHostnames} are always enabled in Azure VNets
 * (Azure DNS is built-in), so these abstract properties are not mapped.</p>
 *
 * <p>Cloud-managed properties mapped back:</p>
 * <ul>
 *   <li>{@code id} <- {@code id}</li>
 *   <li>{@code state} <- {@code provisioningState}</li>
 * </ul>
 */
public class NetworkAdapter implements StandardTypeAdapter<VnetResource> {

    private final VnetResourceType handler;

    public NetworkAdapter(VnetResourceType handler) {
        this.handler = handler;
    }

    @Override
    public String standardTypeName() {
        return "Network";
    }

    @Override
    public String concreteTypeName() {
        return "Vnet";
    }

    @Override
    @SuppressWarnings("unchecked")
    public VnetResource toConcreteProperties(Map<String, Object> abstractProps) {
        var builder = VnetResource.builder();

        // Map name -> name
        var name = (String) abstractProps.get("name");
        if (name != null) {
            builder.name(name);
        }

        // Map cidrBlock -> addressSpaces (single-element list)
        var cidrBlock = (String) abstractProps.get("cidrBlock");
        if (cidrBlock != null) {
            builder.addressSpaces(List.of(cidrBlock));
        }

        // Map tags directly
        var abstractTags = abstractProps.get("tags");
        if (abstractTags instanceof Map<?, ?> rawTags) {
            var tags = new HashMap<String, String>();
            rawTags.forEach((k, v) -> tags.put(String.valueOf(k), String.valueOf(v)));
            builder.tags(tags);
        }

        return builder.build();
    }

    @Override
    public Map<String, Object> toAbstractProperties(VnetResource concrete) {
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
    public ResourceTypeHandler<VnetResource> getConcreteHandler() {
        return handler;
    }
}
