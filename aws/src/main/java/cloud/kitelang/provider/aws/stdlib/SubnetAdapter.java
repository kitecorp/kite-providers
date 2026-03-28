package cloud.kitelang.provider.aws.stdlib;

import cloud.kitelang.provider.ResourceTypeHandler;
import cloud.kitelang.provider.StandardTypeAdapter;
import cloud.kitelang.provider.aws.networking.SubnetResource;
import cloud.kitelang.provider.aws.networking.SubnetResourceType;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapts the standard library {@code Subnet} type to AWS {@code Subnet}.
 *
 * <p>Property mapping:</p>
 * <ul>
 *   <li>{@code networkId} -> {@code vpcId}</li>
 *   <li>{@code cidrBlock} -> {@code cidrBlock}</li>
 *   <li>{@code availabilityZone} -> {@code availabilityZone}</li>
 *   <li>{@code name} -> {@code tags.Name}</li>
 *   <li>{@code tags} -> {@code tags}</li>
 * </ul>
 *
 * <p>Cloud-managed properties mapped back:</p>
 * <ul>
 *   <li>{@code id} <- {@code subnetId}</li>
 *   <li>{@code state} <- {@code state}</li>
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

        // networkId -> vpcId
        var networkId = (String) abstractProps.get("networkId");
        if (networkId != null) {
            builder.vpcId(networkId);
        }

        var cidrBlock = (String) abstractProps.get("cidrBlock");
        if (cidrBlock != null) {
            builder.cidrBlock(cidrBlock);
        }

        var availabilityZone = (String) abstractProps.get("availabilityZone");
        if (availabilityZone != null) {
            builder.availabilityZone(availabilityZone);
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
    public Map<String, Object> toAbstractProperties(SubnetResource concrete) {
        var result = new HashMap<String, Object>();

        if (concrete.getSubnetId() != null) {
            result.put("id", concrete.getSubnetId());
        }
        if (concrete.getState() != null) {
            result.put("state", concrete.getState());
        }

        return result;
    }

    @Override
    public ResourceTypeHandler<SubnetResource> getConcreteHandler() {
        return handler;
    }
}
