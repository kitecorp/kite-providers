package cloud.kitelang.provider.gcp.stdlib;

import cloud.kitelang.provider.ResourceTypeHandler;
import cloud.kitelang.provider.StandardTypeAdapter;
import cloud.kitelang.provider.gcp.networking.SubnetworkResource;
import cloud.kitelang.provider.gcp.networking.SubnetworkResourceType;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapts the standard library {@code Subnet} type to GCP {@code Subnetwork}.
 *
 * <p>Property mapping:</p>
 * <ul>
 *   <li>{@code networkId} -> {@code network}</li>
 *   <li>{@code cidrBlock} -> {@code ipCidrRange}</li>
 *   <li>{@code availabilityZone} -> {@code region} (GCP subnets are regional, not zonal)</li>
 *   <li>{@code name} -> {@code name}</li>
 *   <li>{@code tags} -> not mapped (GCP subnets don't support labels)</li>
 * </ul>
 *
 * <p>Cloud-managed properties mapped back:</p>
 * <ul>
 *   <li>{@code id} <- {@code subnetworkId}</li>
 *   <li>{@code state} <- "available" (GCP subnets are available once created)</li>
 * </ul>
 */
public class SubnetAdapter implements StandardTypeAdapter<SubnetworkResource> {

    private final SubnetworkResourceType handler;

    public SubnetAdapter(SubnetworkResourceType handler) {
        this.handler = handler;
    }

    @Override
    public String standardTypeName() {
        return "Subnet";
    }

    @Override
    public String concreteTypeName() {
        return "Subnetwork";
    }

    @Override
    @SuppressWarnings("unchecked")
    public SubnetworkResource toConcreteProperties(Map<String, Object> abstractProps) {
        var builder = SubnetworkResource.builder();

        // networkId -> network
        var networkId = (String) abstractProps.get("networkId");
        if (networkId != null) {
            builder.network(networkId);
        }

        // cidrBlock -> ipCidrRange
        var cidrBlock = (String) abstractProps.get("cidrBlock");
        if (cidrBlock != null) {
            builder.ipCidrRange(cidrBlock);
        }

        // availabilityZone -> region (GCP subnets are regional)
        // If user provides a zone (e.g., "us-central1-a"), extract the region ("us-central1")
        var availabilityZone = (String) abstractProps.get("availabilityZone");
        if (availabilityZone != null) {
            var region = extractRegionFromZone(availabilityZone);
            builder.region(region);
        }

        var name = (String) abstractProps.get("name");
        if (name != null) {
            builder.name(name);
        }

        return builder.build();
    }

    @Override
    public Map<String, Object> toAbstractProperties(SubnetworkResource concrete) {
        var result = new HashMap<String, Object>();

        if (concrete.getSubnetworkId() != null) {
            result.put("id", concrete.getSubnetworkId());
        }
        // GCP subnets are always available once created
        result.put("state", "available");

        return result;
    }

    @Override
    public ResourceTypeHandler<SubnetworkResource> getConcreteHandler() {
        return handler;
    }

    /**
     * Extract the GCP region from a zone or region string.
     * If the input looks like a zone (e.g., "us-central1-a"), strip the trailing zone letter.
     * If it already looks like a region (e.g., "us-central1"), return as-is.
     */
    private static String extractRegionFromZone(String zoneOrRegion) {
        // GCP zones end with "-[a-z]" appended to the region
        if (zoneOrRegion.matches(".*-[a-z]$")) {
            return zoneOrRegion.substring(0, zoneOrRegion.lastIndexOf('-'));
        }
        return zoneOrRegion;
    }
}
