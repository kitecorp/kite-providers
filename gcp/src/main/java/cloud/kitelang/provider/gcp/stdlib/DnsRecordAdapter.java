package cloud.kitelang.provider.gcp.stdlib;

import cloud.kitelang.provider.ResourceTypeHandler;
import cloud.kitelang.provider.StandardTypeAdapter;
import cloud.kitelang.provider.gcp.dns.ResourceRecordSetResource;
import cloud.kitelang.provider.gcp.dns.ResourceRecordSetResourceType;

import java.util.List;
import java.util.Map;

/**
 * Adapts the standard library {@code DnsRecord} type to GCP {@code ResourceRecordSet}.
 *
 * <p>Property mapping:</p>
 * <ul>
 *   <li>{@code zoneId} -> {@code managedZone}</li>
 *   <li>{@code name} -> {@code name}</li>
 *   <li>{@code recordType} -> {@code type}</li>
 *   <li>{@code ttl} -> {@code ttl}</li>
 *   <li>{@code values} -> {@code rrdatas}</li>
 * </ul>
 *
 * <p>The {@code DnsRecord} standard type has no {@code @cloud} properties,
 * so {@link #toAbstractProperties} returns an empty map.</p>
 */
public class DnsRecordAdapter implements StandardTypeAdapter<ResourceRecordSetResource> {

    private final ResourceRecordSetResourceType handler;

    public DnsRecordAdapter(ResourceRecordSetResourceType handler) {
        this.handler = handler;
    }

    @Override
    public String standardTypeName() {
        return "DnsRecord";
    }

    @Override
    public String concreteTypeName() {
        return "ResourceRecordSet";
    }

    @Override
    @SuppressWarnings("unchecked")
    public ResourceRecordSetResource toConcreteProperties(Map<String, Object> abstractProps) {
        var builder = ResourceRecordSetResource.builder();

        // zoneId -> managedZone
        var zoneId = (String) abstractProps.get("zoneId");
        if (zoneId != null) {
            builder.managedZone(zoneId);
        }

        var name = (String) abstractProps.get("name");
        if (name != null) {
            // GCP requires FQDN ending with a dot
            builder.name(name.endsWith(".") ? name : name + ".");
        }

        // recordType -> type
        var recordType = (String) abstractProps.get("recordType");
        if (recordType != null) {
            builder.type(recordType);
        }

        // ttl: the stdlib schema declares it as number
        var ttl = abstractProps.get("ttl");
        if (ttl instanceof Number number) {
            builder.ttl(number.intValue());
        }

        // values -> rrdatas
        var values = abstractProps.get("values");
        if (values instanceof List<?> list) {
            builder.rrdatas(list.stream()
                    .map(String::valueOf)
                    .toList());
        }

        return builder.build();
    }

    @Override
    public Map<String, Object> toAbstractProperties(ResourceRecordSetResource concrete) {
        // DnsRecord has no @cloud properties, so nothing to map back
        return Map.of();
    }

    @Override
    public ResourceTypeHandler<ResourceRecordSetResource> getConcreteHandler() {
        return handler;
    }
}
