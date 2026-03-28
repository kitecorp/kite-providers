package cloud.kitelang.provider.aws.stdlib;

import cloud.kitelang.provider.ResourceTypeHandler;
import cloud.kitelang.provider.StandardTypeAdapter;
import cloud.kitelang.provider.aws.loadbalancing.LoadBalancerResource;
import cloud.kitelang.provider.aws.loadbalancing.LoadBalancerResourceType;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapts the standard library {@code LoadBalancer} type to AWS {@code LoadBalancer}.
 *
 * <p>Property mapping:</p>
 * <ul>
 *   <li>{@code name} -> {@code name}</li>
 *   <li>{@code lbType} -> {@code type}</li>
 *   <li>{@code internal} -> {@code scheme} ("internal" when true, "internet-facing" when false)</li>
 *   <li>{@code tags} -> {@code tags}</li>
 * </ul>
 *
 * <p>Cloud-managed properties mapped back:</p>
 * <ul>
 *   <li>{@code id} <- {@code arn} (ARN serves as the abstract ID for load balancers)</li>
 *   <li>{@code arn} <- {@code arn}</li>
 *   <li>{@code dnsName} <- {@code dnsName}</li>
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

        var name = (String) abstractProps.get("name");
        if (name != null) {
            builder.name(name);
        }

        // lbType -> type
        var lbType = (String) abstractProps.get("lbType");
        if (lbType != null) {
            builder.type(lbType);
        }

        // internal (boolean) -> scheme (string)
        var internal = abstractProps.get("internal");
        if (internal instanceof Boolean value) {
            builder.scheme(value ? "internal" : "internet-facing");
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

        // ARN serves as the abstract ID
        if (concrete.getArn() != null) {
            result.put("id", concrete.getArn());
            result.put("arn", concrete.getArn());
        }
        if (concrete.getDnsName() != null) {
            result.put("dnsName", concrete.getDnsName());
        }

        return result;
    }

    @Override
    public ResourceTypeHandler<LoadBalancerResource> getConcreteHandler() {
        return handler;
    }
}
