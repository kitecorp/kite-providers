package cloud.kitelang.provider.azure.stdlib;

import cloud.kitelang.provider.ResourceTypeHandler;
import cloud.kitelang.provider.StandardTypeAdapter;
import cloud.kitelang.provider.azure.dns.DnsZoneResource;
import cloud.kitelang.provider.azure.dns.DnsZoneResourceType;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapts the standard library {@code DnsZone} type to Azure {@code DnsZone}.
 *
 * <p>Property mapping:</p>
 * <ul>
 *   <li>{@code name} -> {@code name} (domain name, e.g., "example.com")</li>
 *   <li>{@code comment} -> not mapped (Azure DNS zones do not have a comment field;
 *       use tags as an alternative)</li>
 *   <li>{@code tags} -> {@code tags}</li>
 * </ul>
 *
 * <p>Cloud-managed properties mapped back:</p>
 * <ul>
 *   <li>{@code id} <- {@code id}</li>
 *   <li>{@code nameServers} <- {@code nameServers} (joined as comma-separated string)</li>
 * </ul>
 */
public class DnsZoneAdapter implements StandardTypeAdapter<DnsZoneResource> {

    private final DnsZoneResourceType handler;

    public DnsZoneAdapter(DnsZoneResourceType handler) {
        this.handler = handler;
    }

    @Override
    public String standardTypeName() {
        return "DnsZone";
    }

    @Override
    public String concreteTypeName() {
        return "DnsZone";
    }

    @Override
    @SuppressWarnings("unchecked")
    public DnsZoneResource toConcreteProperties(Map<String, Object> abstractProps) {
        var builder = DnsZoneResource.builder();

        // Map name -> name (the domain name, e.g., "example.com")
        var name = (String) abstractProps.get("name");
        if (name != null) {
            builder.name(name);
        }

        // comment -> stored in tags as "comment" key if provided
        var comment = (String) abstractProps.get("comment");
        var abstractTags = abstractProps.get("tags");

        var tags = new HashMap<String, String>();
        if (abstractTags instanceof Map<?, ?> rawTags) {
            rawTags.forEach((k, v) -> tags.put(String.valueOf(k), String.valueOf(v)));
        }
        if (comment != null && !comment.isBlank()) {
            tags.put("comment", comment);
        }
        if (!tags.isEmpty()) {
            builder.tags(tags);
        }

        return builder.build();
    }

    @Override
    public Map<String, Object> toAbstractProperties(DnsZoneResource concrete) {
        var result = new HashMap<String, Object>();

        if (concrete.getId() != null) {
            result.put("id", concrete.getId());
        }
        // DnsZone schema declares nameServers as a single string; join the list
        if (concrete.getNameServers() != null && !concrete.getNameServers().isEmpty()) {
            result.put("nameServers", String.join(",", concrete.getNameServers()));
        }

        return result;
    }

    @Override
    public ResourceTypeHandler<DnsZoneResource> getConcreteHandler() {
        return handler;
    }
}
