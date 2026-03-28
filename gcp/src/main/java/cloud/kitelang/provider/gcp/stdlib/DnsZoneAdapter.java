package cloud.kitelang.provider.gcp.stdlib;

import cloud.kitelang.provider.ResourceTypeHandler;
import cloud.kitelang.provider.StandardTypeAdapter;
import cloud.kitelang.provider.gcp.dns.ManagedZoneResource;
import cloud.kitelang.provider.gcp.dns.ManagedZoneResourceType;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapts the standard library {@code DnsZone} type to GCP {@code ManagedZone}.
 *
 * <p>Property mapping:</p>
 * <ul>
 *   <li>{@code name} -> {@code dnsName} (the DNS domain name; also used to derive the zone name)</li>
 *   <li>{@code comment} -> {@code description}</li>
 *   <li>{@code tags} -> {@code labels}</li>
 * </ul>
 *
 * <p>Cloud-managed properties mapped back:</p>
 * <ul>
 *   <li>{@code id} <- {@code managedZoneId}</li>
 *   <li>{@code nameServers} <- {@code nameServers} (joined as comma-separated string)</li>
 * </ul>
 */
public class DnsZoneAdapter implements StandardTypeAdapter<ManagedZoneResource> {

    private final ManagedZoneResourceType handler;

    public DnsZoneAdapter(ManagedZoneResourceType handler) {
        this.handler = handler;
    }

    @Override
    public String standardTypeName() {
        return "DnsZone";
    }

    @Override
    public String concreteTypeName() {
        return "ManagedZone";
    }

    @Override
    @SuppressWarnings("unchecked")
    public ManagedZoneResource toConcreteProperties(Map<String, Object> abstractProps) {
        var builder = ManagedZoneResource.builder();

        var name = (String) abstractProps.get("name");
        if (name != null) {
            // GCP requires dnsName to end with a dot
            var dnsName = name.endsWith(".") ? name : name + ".";
            builder.dnsName(dnsName);

            // Derive zone name from DNS name: replace dots with dashes, remove trailing dash
            var zoneName = name.replace(".", "-");
            if (zoneName.endsWith("-")) {
                zoneName = zoneName.substring(0, zoneName.length() - 1);
            }
            builder.name(zoneName);
        }

        // comment -> description
        var comment = (String) abstractProps.get("comment");
        if (comment != null) {
            builder.description(comment);
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
    public Map<String, Object> toAbstractProperties(ManagedZoneResource concrete) {
        var result = new HashMap<String, Object>();

        if (concrete.getManagedZoneId() != null) {
            result.put("id", concrete.getManagedZoneId());
        }
        // DnsZone schema declares nameServers as a single string; join the list
        if (concrete.getNameServers() != null && !concrete.getNameServers().isEmpty()) {
            result.put("nameServers", String.join(",", concrete.getNameServers()));
        }

        return result;
    }

    @Override
    public ResourceTypeHandler<ManagedZoneResource> getConcreteHandler() {
        return handler;
    }
}
