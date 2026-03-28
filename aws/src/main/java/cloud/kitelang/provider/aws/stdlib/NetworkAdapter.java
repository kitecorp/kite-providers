package cloud.kitelang.provider.aws.stdlib;

import cloud.kitelang.provider.ResourceTypeHandler;
import cloud.kitelang.provider.StandardTypeAdapter;
import cloud.kitelang.provider.aws.networking.VpcResource;
import cloud.kitelang.provider.aws.networking.VpcResourceType;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapts the standard library {@code Network} type to AWS {@code Vpc}.
 *
 * <p>Property mapping:</p>
 * <ul>
 *   <li>{@code cidrBlock} -> {@code cidrBlock}</li>
 *   <li>{@code enableDnsSupport} -> {@code enableDnsSupport}</li>
 *   <li>{@code enableDnsHostnames} -> {@code enableDnsHostnames}</li>
 *   <li>{@code name} -> {@code tags.Name}</li>
 *   <li>{@code tags} -> {@code tags}</li>
 * </ul>
 *
 * <p>Cloud-managed properties mapped back:</p>
 * <ul>
 *   <li>{@code id} <- {@code vpcId}</li>
 *   <li>{@code state} <- {@code state}</li>
 * </ul>
 */
public class NetworkAdapter implements StandardTypeAdapter<VpcResource> {

    private final VpcResourceType handler;

    public NetworkAdapter(VpcResourceType handler) {
        this.handler = handler;
    }

    @Override
    public String standardTypeName() {
        return "Network";
    }

    @Override
    public String concreteTypeName() {
        return "Vpc";
    }

    @Override
    @SuppressWarnings("unchecked")
    public VpcResource toConcreteProperties(Map<String, Object> abstractProps) {
        var builder = VpcResource.builder();

        var cidrBlock = (String) abstractProps.get("cidrBlock");
        if (cidrBlock != null) {
            builder.cidrBlock(cidrBlock);
        }

        var enableDnsSupport = abstractProps.get("enableDnsSupport");
        if (enableDnsSupport instanceof Boolean value) {
            builder.enableDnsSupport(value);
        }

        var enableDnsHostnames = abstractProps.get("enableDnsHostnames");
        if (enableDnsHostnames instanceof Boolean value) {
            builder.enableDnsHostnames(value);
        }

        // Map name and tags -> tags with Name key
        var tags = new HashMap<String, String>();
        var abstractTags = abstractProps.get("tags");
        if (abstractTags instanceof Map<?, ?> rawTags) {
            rawTags.forEach((k, v) -> tags.put(String.valueOf(k), String.valueOf(v)));
        }
        var name = (String) abstractProps.get("name");
        if (name != null) {
            tags.put("Name", name);
        }
        if (!tags.isEmpty()) {
            builder.tags(tags);
        }

        return builder.build();
    }

    @Override
    public Map<String, Object> toAbstractProperties(VpcResource concrete) {
        var result = new HashMap<String, Object>();

        if (concrete.getVpcId() != null) {
            result.put("id", concrete.getVpcId());
        }
        if (concrete.getState() != null) {
            result.put("state", concrete.getState());
        }

        return result;
    }

    @Override
    public ResourceTypeHandler<VpcResource> getConcreteHandler() {
        return handler;
    }
}
