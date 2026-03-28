package cloud.kitelang.provider.gcp.stdlib;

import cloud.kitelang.provider.ResourceTypeHandler;
import cloud.kitelang.provider.StandardTypeAdapter;
import cloud.kitelang.provider.gcp.networking.VpcNetworkResource;
import cloud.kitelang.provider.gcp.networking.VpcNetworkResourceType;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapts the standard library {@code Network} type to GCP {@code VpcNetwork}.
 *
 * <p>GCP VPC networks do not have a CIDR block at the network level (unlike AWS).
 * The {@code cidrBlock} from the abstract type is not mapped. IP ranges are defined
 * at the subnetwork level in GCP.</p>
 *
 * <p>Property mapping:</p>
 * <ul>
 *   <li>{@code name} -> {@code name}</li>
 *   <li>{@code cidrBlock} -> not mapped (GCP VPCs don't have network-level CIDR)</li>
 *   <li>{@code enableDnsSupport} -> not mapped (always enabled in GCP)</li>
 *   <li>{@code enableDnsHostnames} -> not mapped (always enabled in GCP)</li>
 *   <li>{@code tags} -> not mapped (GCP VPCs don't support labels)</li>
 * </ul>
 *
 * <p>Cloud-managed properties mapped back:</p>
 * <ul>
 *   <li>{@code id} <- {@code networkId}</li>
 *   <li>{@code state} <- "available" (GCP networks are always available once created)</li>
 * </ul>
 */
public class NetworkAdapter implements StandardTypeAdapter<VpcNetworkResource> {

    private final VpcNetworkResourceType handler;

    public NetworkAdapter(VpcNetworkResourceType handler) {
        this.handler = handler;
    }

    @Override
    public String standardTypeName() {
        return "Network";
    }

    @Override
    public String concreteTypeName() {
        return "VpcNetwork";
    }

    @Override
    @SuppressWarnings("unchecked")
    public VpcNetworkResource toConcreteProperties(Map<String, Object> abstractProps) {
        var builder = VpcNetworkResource.builder();

        var name = (String) abstractProps.get("name");
        if (name != null) {
            builder.name(name);
        }

        // GCP VPCs with manual subnets don't auto-create subnetworks
        // If a cidrBlock is provided, assume the user wants manual subnet control
        var cidrBlock = (String) abstractProps.get("cidrBlock");
        if (cidrBlock != null) {
            builder.autoCreateSubnetworks(false);
        }

        return builder.build();
    }

    @Override
    public Map<String, Object> toAbstractProperties(VpcNetworkResource concrete) {
        var result = new HashMap<String, Object>();

        if (concrete.getNetworkId() != null) {
            result.put("id", concrete.getNetworkId());
        }
        // GCP networks are always available once created
        result.put("state", "available");

        return result;
    }

    @Override
    public ResourceTypeHandler<VpcNetworkResource> getConcreteHandler() {
        return handler;
    }
}
