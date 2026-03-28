package cloud.kitelang.provider.aws.stdlib;

import cloud.kitelang.provider.ResourceTypeHandler;
import cloud.kitelang.provider.StandardTypeAdapter;
import cloud.kitelang.provider.aws.dns.RecordSetResource;
import cloud.kitelang.provider.aws.dns.RecordSetResourceType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapts the standard library {@code DnsRecord} type to AWS {@code RecordSet}.
 *
 * <p>Property mapping:</p>
 * <ul>
 *   <li>{@code zoneId} -> {@code hostedZoneId}</li>
 *   <li>{@code name} -> {@code name}</li>
 *   <li>{@code recordType} -> {@code type}</li>
 *   <li>{@code ttl} -> {@code ttl}</li>
 *   <li>{@code values} -> {@code records}</li>
 * </ul>
 *
 * <p>The {@code DnsRecord} standard type has no {@code @cloud} properties,
 * so {@link #toAbstractProperties} returns an empty map.</p>
 */
public class DnsRecordAdapter implements StandardTypeAdapter<RecordSetResource> {

    private final RecordSetResourceType handler;

    public DnsRecordAdapter(RecordSetResourceType handler) {
        this.handler = handler;
    }

    @Override
    public String standardTypeName() {
        return "DnsRecord";
    }

    @Override
    public String concreteTypeName() {
        return "RecordSet";
    }

    @Override
    @SuppressWarnings("unchecked")
    public RecordSetResource toConcreteProperties(Map<String, Object> abstractProps) {
        var builder = RecordSetResource.builder();

        // zoneId -> hostedZoneId
        var zoneId = (String) abstractProps.get("zoneId");
        if (zoneId != null) {
            builder.hostedZoneId(zoneId);
        }

        var name = (String) abstractProps.get("name");
        if (name != null) {
            builder.name(name);
        }

        // recordType -> type
        var recordType = (String) abstractProps.get("recordType");
        if (recordType != null) {
            builder.type(recordType);
        }

        // ttl: the stdlib schema declares it as number, so it could arrive as Integer, Long, or Double
        var ttl = abstractProps.get("ttl");
        if (ttl instanceof Number number) {
            builder.ttl(number.longValue());
        }

        // values -> records
        var values = abstractProps.get("values");
        if (values instanceof List<?> list) {
            builder.records(list.stream()
                    .map(String::valueOf)
                    .toList());
        }

        return builder.build();
    }

    @Override
    public Map<String, Object> toAbstractProperties(RecordSetResource concrete) {
        // DnsRecord has no @cloud properties, so nothing to map back
        return Map.of();
    }

    @Override
    public ResourceTypeHandler<RecordSetResource> getConcreteHandler() {
        return handler;
    }
}
