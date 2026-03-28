package cloud.kitelang.provider.azure.stdlib;

import cloud.kitelang.provider.ResourceTypeHandler;
import cloud.kitelang.provider.StandardTypeAdapter;
import cloud.kitelang.provider.azure.loadbalancing.LoadBalancerResource;
import cloud.kitelang.provider.azure.loadbalancing.LoadBalancerResourceType;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapts the standard library {@code LoadBalancer} type to Azure {@code LoadBalancer}.
 *
 * <p>Property mapping:</p>
 * <ul>
 *   <li>{@code name} -> {@code name}</li>
 *   <li>{@code lbType} -> {@code sku} ("application" maps to "Standard", "network" maps to "Basic")</li>
 *   <li>{@code internal} -> {@code tier} hint (internal LBs are regional by default)</li>
 *   <li>{@code tags} -> {@code tags}</li>
 * </ul>
 *
 * <p>Note: Azure Load Balancers do not have a direct "application" vs "network" distinction
 * like AWS ALB/NLB. The Azure LB SKU (Basic/Standard) determines feature set instead.
 * The {@code internal} flag influences frontend configuration rather than a scheme field.</p>
 *
 * <p>Cloud-managed properties mapped back:</p>
 * <ul>
 *   <li>{@code id} <- {@code id}</li>
 *   <li>{@code arn} <- {@code id} (Azure resource ID serves as the unique resource identifier)</li>
 *   <li>{@code dnsName} <- not directly available (Azure LBs get DNS via associated Public IP)</li>
 * </ul>
 */
public class LoadBalancerAdapter implements StandardTypeAdapter<LoadBalancerResource> {

    private final LoadBalancerResourceType handler;

    public LoadBalancerAdapter(LoadBalancerResourceType handler) {
        this.handler = handler;
    }

    @Override
    public String standardTypeName() {
        return "LoadBalancer";
    }

    @Override
    public String concreteTypeName() {
        return "LoadBalancer";
    }

    @Override
    @SuppressWarnings("unchecked")
    public LoadBalancerResource toConcreteProperties(Map<String, Object> abstractProps) {
        var builder = LoadBalancerResource.builder();

        // Map name -> name
        var name = (String) abstractProps.get("name");
        if (name != null) {
            builder.name(name);
        }

        // Map lbType -> sku
        // "application" (layer 7) -> Standard (most feature-rich Azure SKU)
        // "network" (layer 4) -> Standard (Azure Standard LB handles L4 natively)
        // Default to Standard for any unrecognized type
        var lbType = (String) abstractProps.get("lbType");
        if ("network".equalsIgnoreCase(lbType)) {
            builder.sku("Standard");
        } else {
            builder.sku("Standard");
        }

        // Map internal (boolean) -> hint for frontend type
        // Azure does not have a "scheme" field; internal vs. public is determined
        // by whether the frontend uses a subnetId or publicIpAddressId.
        // We store the preference in the tier field as a convention.
        var internal = abstractProps.get("internal");
        if (internal instanceof Boolean value && value) {
            builder.tier("Regional");
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
    public Map<String, Object> toAbstractProperties(LoadBalancerResource concrete) {
        var result = new HashMap<String, Object>();

        // Azure resource ID serves as the abstract ID
        if (concrete.getId() != null) {
            result.put("id", concrete.getId());
            result.put("arn", concrete.getId());
        }
        // Azure LBs do not have a direct dnsName property;
        // DNS is provided via the associated Public IP address.

        return result;
    }

    @Override
    public ResourceTypeHandler<LoadBalancerResource> getConcreteHandler() {
        return handler;
    }
}
