package cloud.kitelang.provider.gcp.stdlib;

import cloud.kitelang.provider.ResourceTypeHandler;
import cloud.kitelang.provider.StandardTypeAdapter;
import cloud.kitelang.provider.gcp.loadbalancing.ForwardingRuleResource;
import cloud.kitelang.provider.gcp.loadbalancing.ForwardingRuleResourceType;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapts the standard library {@code LoadBalancer} type to GCP {@code ForwardingRule}.
 *
 * <p>GCP load balancing is split across multiple resources (forwarding rules, backend services,
 * target proxies, etc.). The forwarding rule is the closest equivalent to the abstract
 * load balancer concept as it defines the entry point for traffic.</p>
 *
 * <p>Property mapping:</p>
 * <ul>
 *   <li>{@code name} -> {@code name}</li>
 *   <li>{@code lbType} -> {@code ipProtocol} ("application" -> "TCP", "network" -> "TCP")</li>
 *   <li>{@code internal} -> {@code loadBalancingScheme} (true -> "INTERNAL", false -> "EXTERNAL")</li>
 *   <li>{@code tags} -> {@code labels}</li>
 * </ul>
 *
 * <p>Cloud-managed properties mapped back:</p>
 * <ul>
 *   <li>{@code id} <- {@code forwardingRuleId}</li>
 *   <li>{@code arn} <- {@code selfLink} (GCP uses self-links instead of ARNs)</li>
 *   <li>{@code dnsName} <- {@code ipAddress} (the IP address serves as the DNS entry point)</li>
 * </ul>
 */
public class LoadBalancerAdapter implements StandardTypeAdapter<ForwardingRuleResource> {

    private final ForwardingRuleResourceType handler;

    public LoadBalancerAdapter(ForwardingRuleResourceType handler) {
        this.handler = handler;
    }

    @Override
    public String standardTypeName() {
        return "LoadBalancer";
    }

    @Override
    public String concreteTypeName() {
        return "ForwardingRule";
    }

    @Override
    @SuppressWarnings("unchecked")
    public ForwardingRuleResource toConcreteProperties(Map<String, Object> abstractProps) {
        var builder = ForwardingRuleResource.builder();

        var name = (String) abstractProps.get("name");
        if (name != null) {
            builder.name(name);
        }

        // lbType -> ipProtocol (GCP forwarding rules use protocol, not LB type)
        var lbType = (String) abstractProps.get("lbType");
        if (lbType != null) {
            builder.ipProtocol("TCP"); // Both application and network LBs typically use TCP
        }

        // internal (boolean) -> loadBalancingScheme (string)
        var internal = abstractProps.get("internal");
        if (internal instanceof Boolean value) {
            builder.loadBalancingScheme(value ? "INTERNAL" : "EXTERNAL");
        }

        // tags -> labels
        var abstractTags = abstractProps.get("tags");
        if (abstractTags instanceof Map<?, ?> rawTags) {
            var labels = new HashMap<String, String>();
            rawTags.forEach((k, v) -> labels.put(String.valueOf(k), String.valueOf(v)));
            builder.labels(labels);
        }

        return builder.build();
    }

    @Override
    public Map<String, Object> toAbstractProperties(ForwardingRuleResource concrete) {
        var result = new HashMap<String, Object>();

        if (concrete.getForwardingRuleId() != null) {
            result.put("id", concrete.getForwardingRuleId());
        }
        if (concrete.getSelfLink() != null) {
            result.put("arn", concrete.getSelfLink());
        }
        if (concrete.getIpAddress() != null) {
            result.put("dnsName", concrete.getIpAddress());
        }

        return result;
    }

    @Override
    public ResourceTypeHandler<ForwardingRuleResource> getConcreteHandler() {
        return handler;
    }
}
