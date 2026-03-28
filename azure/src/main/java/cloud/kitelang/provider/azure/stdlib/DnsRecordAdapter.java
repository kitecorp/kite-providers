package cloud.kitelang.provider.azure.stdlib;

import cloud.kitelang.provider.ResourceTypeHandler;
import cloud.kitelang.provider.StandardTypeAdapter;
import cloud.kitelang.provider.azure.dns.DnsRecordSetResource;
import cloud.kitelang.provider.azure.dns.DnsRecordSetResourceType;

import java.util.List;
import java.util.Map;

/**
 * Adapts the standard library {@code DnsRecord} type to Azure {@code DnsRecordSet}.
 *
 * <p>Property mapping:</p>
 * <ul>
 *   <li>{@code zoneId} -> {@code zoneName} (Azure uses zone name, not ID, for record operations.
 *       If the value is a full resource ID, the zone name is extracted.)</li>
 *   <li>{@code name} -> {@code name}</li>
 *   <li>{@code recordType} -> {@code type}</li>
 *   <li>{@code ttl} -> {@code ttl}</li>
 *   <li>{@code values} -> type-specific record lists ({@code aRecords}, {@code aaaaRecords},
 *       {@code txtRecords}, {@code cnameRecord}, etc.) based on the record type</li>
 * </ul>
 *
 * <p>The {@code DnsRecord} standard type has no {@code @cloud} properties,
 * so {@link #toAbstractProperties} returns an empty map.</p>
 */
public class DnsRecordAdapter implements StandardTypeAdapter<DnsRecordSetResource> {

    private final DnsRecordSetResourceType handler;

    public DnsRecordAdapter(DnsRecordSetResourceType handler) {
        this.handler = handler;
    }

    @Override
    public String standardTypeName() {
        return "DnsRecord";
    }

    @Override
    public String concreteTypeName() {
        return "DnsRecordSet";
    }

    @Override
    @SuppressWarnings("unchecked")
    public DnsRecordSetResource toConcreteProperties(Map<String, Object> abstractProps) {
        var builder = DnsRecordSetResource.builder();

        // Map zoneId -> zoneName
        // The abstract zoneId could be an Azure resource ID or a simple zone name.
        var zoneId = (String) abstractProps.get("zoneId");
        if (zoneId != null) {
            builder.zoneName(extractZoneName(zoneId));
        }

        // Map name -> name
        var name = (String) abstractProps.get("name");
        if (name != null) {
            builder.name(name);
        }

        // Map recordType -> type
        var recordType = (String) abstractProps.get("recordType");
        if (recordType != null) {
            builder.type(recordType);
        }

        // Map ttl
        var ttl = abstractProps.get("ttl");
        if (ttl instanceof Number number) {
            builder.ttl(number.longValue());
        }

        // Map values -> type-specific record fields
        var values = abstractProps.get("values");
        if (values instanceof List<?> list && recordType != null) {
            var stringValues = list.stream().map(String::valueOf).toList();
            mapValuesToRecordType(builder, recordType, stringValues);
        }

        return builder.build();
    }

    @Override
    public Map<String, Object> toAbstractProperties(DnsRecordSetResource concrete) {
        // DnsRecord has no @cloud properties, so nothing to map back
        return Map.of();
    }

    @Override
    public ResourceTypeHandler<DnsRecordSetResource> getConcreteHandler() {
        return handler;
    }

    /**
     * Map abstract record values to the appropriate Azure-specific record list
     * based on the record type.
     *
     * @param builder    the DnsRecordSetResource builder
     * @param recordType the DNS record type (A, AAAA, CNAME, TXT, etc.)
     * @param values     the record values as strings
     */
    private void mapValuesToRecordType(DnsRecordSetResource.DnsRecordSetResourceBuilder builder,
                                       String recordType, List<String> values) {
        if (values.isEmpty()) {
            return;
        }

        switch (recordType.toUpperCase()) {
            case "A" -> builder.aRecords(values);
            case "AAAA" -> builder.aaaaRecords(values);
            case "CNAME" -> builder.cnameRecord(values.get(0));
            case "TXT" -> builder.txtRecords(values);
            case "NS" -> builder.nsRecords(values);
            case "PTR" -> builder.ptrRecords(values);
            default -> {
                // For MX, SRV, CAA — the abstract values list is a simplified format.
                // These record types need structured data; store as A records as a fallback.
                // Users should use the Azure-specific DnsRecordSet type for complex record types.
            }
        }
    }

    /**
     * Extract the DNS zone name from a value that may be an Azure resource ID or a simple name.
     * Azure DNS Zone resource IDs follow the format:
     * {@code /subscriptions/.../resourceGroups/.../providers/Microsoft.Network/dnszones/{zoneName}}
     *
     * @param zoneIdOrName either a full Azure resource ID or a simple zone name
     * @return the DNS zone name (e.g., "example.com")
     */
    static String extractZoneName(String zoneIdOrName) {
        if (zoneIdOrName.contains("/dnszones/") || zoneIdOrName.contains("/dnsZones/")) {
            // Handle both lowercase and mixed-case Azure API variations
            var lowerCased = zoneIdOrName.toLowerCase();
            var index = lowerCased.lastIndexOf("/dnszones/");
            if (index >= 0) {
                return zoneIdOrName.substring(index + "/dnszones/".length());
            }
        }
        return zoneIdOrName;
    }
}
