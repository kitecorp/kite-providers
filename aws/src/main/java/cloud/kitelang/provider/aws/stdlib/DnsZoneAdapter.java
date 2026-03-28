package cloud.kitelang.provider.aws.stdlib;

import cloud.kitelang.provider.ResourceTypeHandler;
import cloud.kitelang.provider.StandardTypeAdapter;
import cloud.kitelang.provider.aws.dns.HostedZoneResource;
import cloud.kitelang.provider.aws.dns.HostedZoneResourceType;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapts the standard library {@code DnsZone} type to AWS {@code HostedZone}.
 *
 * <p>Property mapping:</p>
 * <ul>
 *   <li>{@code name} -> {@code name}</li>
 *   <li>{@code comment} -> {@code comment}</li>
 *   <li>{@code tags} -> {@code tags}</li>
 * </ul>
 *
 * <p>Cloud-managed properties mapped back:</p>
 * <ul>
 *   <li>{@code id} <- {@code hostedZoneId}</li>
 *   <li>{@code nameServers} <- {@code nameServers} (joined as comma-separated string)</li>
 * </ul>
 */
public class DnsZoneAdapter implements StandardTypeAdapter<HostedZoneResource> {

    private final HostedZoneResourceType handler;

    public DnsZoneAdapter(HostedZoneResourceType handler) {
        this.handler = handler;
    }

    @Override
    public String standardTypeName() {
        return "DnsZone";
    }

    @Override
    public String concreteTypeName() {
        return "HostedZone";
    }

    @Override
    @SuppressWarnings("unchecked")
    public HostedZoneResource toConcreteProperties(Map<String, Object> abstractProps) {
        var builder = HostedZoneResource.builder();

        var name = (String) abstractProps.get("name");
        if (name != null) {
            builder.name(name);
        }

        var comment = (String) abstractProps.get("comment");
        if (comment != null) {
            builder.comment(comment);
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
    public Map<String, Object> toAbstractProperties(HostedZoneResource concrete) {
        var result = new HashMap<String, Object>();

        if (concrete.getHostedZoneId() != null) {
            result.put("id", concrete.getHostedZoneId());
        }
        // DnsZone schema declares nameServers as a single string; join the list
        if (concrete.getNameServers() != null && !concrete.getNameServers().isEmpty()) {
            result.put("nameServers", String.join(",", concrete.getNameServers()));
        }

        return result;
    }

    @Override
    public ResourceTypeHandler<HostedZoneResource> getConcreteHandler() {
        return handler;
    }
}
