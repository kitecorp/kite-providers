package cloud.kitelang.provider.azure;

import cloud.kitelang.provider.Diagnostic;
import cloud.kitelang.provider.ResourceTypeHandler;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.dns.DnsZoneManager;
import com.azure.resourcemanager.dns.models.*;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ResourceTypeHandler for Azure DNS Record Set.
 * Implements CRUD operations using Azure DNS SDK.
 */
@Log4j2
public class DnsRecordSetResourceType extends ResourceTypeHandler<DnsRecordSetResource> {

    private static final Set<String> VALID_TYPES = Set.of(
            "A", "AAAA", "CNAME", "MX", "TXT", "NS", "SRV", "CAA", "PTR", "SOA"
    );

    private final DnsZoneManager dnsManager;

    public DnsRecordSetResourceType() {
        var credential = new DefaultAzureCredentialBuilder().build();

        String subscriptionId = System.getenv("AZURE_SUBSCRIPTION_ID");
        if (subscriptionId == null || subscriptionId.isBlank()) {
            throw new IllegalStateException(
                    "AZURE_SUBSCRIPTION_ID environment variable must be set");
        }

        String tenantId = System.getenv("AZURE_TENANT_ID");
        var profile = new AzureProfile(tenantId, subscriptionId, AzureEnvironment.AZURE);

        this.dnsManager = DnsZoneManager.authenticate(credential, profile);
    }

    public DnsRecordSetResourceType(DnsZoneManager dnsManager) {
        this.dnsManager = dnsManager;
    }

    @Override
    public DnsRecordSetResource create(DnsRecordSetResource resource) {
        log.info("Creating DNS Record Set: {}.{} ({})", resource.getName(), resource.getZoneName(), resource.getType());

        var zone = dnsManager.zones().getByResourceGroup(resource.getResourceGroup(), resource.getZoneName());
        if (zone == null) {
            throw new RuntimeException("DNS Zone not found: " + resource.getZoneName());
        }

        createOrUpdateRecordSet(zone, resource);

        log.info("Created DNS Record Set: {}.{}", resource.getName(), resource.getZoneName());

        return read(resource);
    }

    @Override
    public DnsRecordSetResource read(DnsRecordSetResource resource) {
        if (resource.getZoneName() == null || resource.getResourceGroup() == null ||
            resource.getName() == null || resource.getType() == null) {
            log.warn("Cannot read DNS Record Set without zoneName, resourceGroup, name, and type");
            return null;
        }

        log.info("Reading DNS Record Set: {}.{} ({})", resource.getName(), resource.getZoneName(), resource.getType());

        try {
            var zone = dnsManager.zones().getByResourceGroup(resource.getResourceGroup(), resource.getZoneName());
            if (zone == null) {
                return null;
            }

            return readRecordSet(zone, resource.getName(), resource.getType());

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("ResourceNotFound")) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public DnsRecordSetResource update(DnsRecordSetResource resource) {
        log.info("Updating DNS Record Set: {}.{} ({})", resource.getName(), resource.getZoneName(), resource.getType());

        var zone = dnsManager.zones().getByResourceGroup(resource.getResourceGroup(), resource.getZoneName());
        if (zone == null) {
            throw new RuntimeException("DNS Zone not found: " + resource.getZoneName());
        }

        createOrUpdateRecordSet(zone, resource);

        return read(resource);
    }

    @Override
    public boolean delete(DnsRecordSetResource resource) {
        if (resource.getZoneName() == null || resource.getResourceGroup() == null ||
            resource.getName() == null || resource.getType() == null) {
            log.warn("Cannot delete DNS Record Set without zoneName, resourceGroup, name, and type");
            return false;
        }

        log.info("Deleting DNS Record Set: {}.{} ({})", resource.getName(), resource.getZoneName(), resource.getType());

        try {
            var zone = dnsManager.zones().getByResourceGroup(resource.getResourceGroup(), resource.getZoneName());
            if (zone == null) {
                return false;
            }

            var update = zone.update();

            switch (resource.getType().toUpperCase()) {
                case "A" -> update.withoutARecordSet(resource.getName());
                case "AAAA" -> update.withoutAaaaRecordSet(resource.getName());
                case "CNAME" -> update.withoutCNameRecordSet(resource.getName());
                case "MX" -> update.withoutMXRecordSet(resource.getName());
                case "TXT" -> update.withoutTxtRecordSet(resource.getName());
                case "NS" -> update.withoutNSRecordSet(resource.getName());
                case "SRV" -> update.withoutSrvRecordSet(resource.getName());
                case "CAA" -> update.withoutCaaRecordSet(resource.getName());
                case "PTR" -> update.withoutPtrRecordSet(resource.getName());
                default -> throw new RuntimeException("Unsupported record type for deletion: " + resource.getType());
            }

            update.apply();

            log.info("Deleted DNS Record Set: {}.{}", resource.getName(), resource.getZoneName());
            return true;

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("ResourceNotFound")) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public List<Diagnostic> validate(DnsRecordSetResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getZoneName() == null || resource.getZoneName().isBlank()) {
            diagnostics.add(Diagnostic.error("zoneName is required")
                    .withProperty("zoneName"));
        }

        if (resource.getResourceGroup() == null || resource.getResourceGroup().isBlank()) {
            diagnostics.add(Diagnostic.error("resourceGroup is required")
                    .withProperty("resourceGroup"));
        }

        if (resource.getName() == null || resource.getName().isBlank()) {
            diagnostics.add(Diagnostic.error("name is required")
                    .withProperty("name"));
        }

        if (resource.getType() == null || resource.getType().isBlank()) {
            diagnostics.add(Diagnostic.error("type is required")
                    .withProperty("type"));
        } else if (!VALID_TYPES.contains(resource.getType().toUpperCase())) {
            diagnostics.add(Diagnostic.error("Invalid record type",
                    "Valid values: " + String.join(", ", VALID_TYPES))
                    .withProperty("type"));
        }

        // TTL required unless alias
        if (resource.getTargetResourceId() == null && resource.getTtl() == null) {
            diagnostics.add(Diagnostic.error("ttl is required when not using alias")
                    .withProperty("ttl"));
        }

        // Type-specific validation
        if (resource.getType() != null) {
            switch (resource.getType().toUpperCase()) {
                case "A" -> {
                    if ((resource.getARecords() == null || resource.getARecords().isEmpty()) &&
                        resource.getTargetResourceId() == null) {
                        diagnostics.add(Diagnostic.error("aRecords or targetResourceId is required for A records")
                                .withProperty("aRecords"));
                    }
                }
                case "AAAA" -> {
                    if ((resource.getAaaaRecords() == null || resource.getAaaaRecords().isEmpty()) &&
                        resource.getTargetResourceId() == null) {
                        diagnostics.add(Diagnostic.error("aaaaRecords or targetResourceId is required for AAAA records")
                                .withProperty("aaaaRecords"));
                    }
                }
                case "CNAME" -> {
                    if (resource.getCnameRecord() == null && resource.getTargetResourceId() == null) {
                        diagnostics.add(Diagnostic.error("cnameRecord or targetResourceId is required for CNAME records")
                                .withProperty("cnameRecord"));
                    }
                }
                case "MX" -> {
                    if (resource.getMxRecords() == null || resource.getMxRecords().isEmpty()) {
                        diagnostics.add(Diagnostic.error("mxRecords is required for MX records")
                                .withProperty("mxRecords"));
                    }
                }
                case "TXT" -> {
                    if (resource.getTxtRecords() == null || resource.getTxtRecords().isEmpty()) {
                        diagnostics.add(Diagnostic.error("txtRecords is required for TXT records")
                                .withProperty("txtRecords"));
                    }
                }
            }
        }

        return diagnostics;
    }

    private void createOrUpdateRecordSet(DnsZone zone, DnsRecordSetResource resource) {
        var update = zone.update();

        var ttl = resource.getTtl() != null ? resource.getTtl() : 3600L;

        switch (resource.getType().toUpperCase()) {
            case "A" -> {
                if (resource.getARecords() != null && !resource.getARecords().isEmpty()) {
                    // Add first IP, then chain the rest with TTL at the end
                    var firstIp = resource.getARecords().get(0);
                    var aRecordDef = update.defineARecordSet(resource.getName())
                            .withIPv4Address(firstIp);
                    for (int i = 1; i < resource.getARecords().size(); i++) {
                        aRecordDef = aRecordDef.withIPv4Address(resource.getARecords().get(i));
                    }
                    aRecordDef.withTimeToLive(ttl).attach();
                }
            }
            case "AAAA" -> {
                if (resource.getAaaaRecords() != null && !resource.getAaaaRecords().isEmpty()) {
                    var firstIp = resource.getAaaaRecords().get(0);
                    var aaaaRecordDef = update.defineAaaaRecordSet(resource.getName())
                            .withIPv6Address(firstIp);
                    for (int i = 1; i < resource.getAaaaRecords().size(); i++) {
                        aaaaRecordDef = aaaaRecordDef.withIPv6Address(resource.getAaaaRecords().get(i));
                    }
                    aaaaRecordDef.withTimeToLive(ttl).attach();
                }
            }
            case "CNAME" -> {
                if (resource.getCnameRecord() != null) {
                    update.defineCNameRecordSet(resource.getName())
                            .withAlias(resource.getCnameRecord())
                            .withTimeToLive(ttl)
                            .attach();
                }
            }
            case "MX" -> {
                if (resource.getMxRecords() != null && !resource.getMxRecords().isEmpty()) {
                    var firstMx = resource.getMxRecords().get(0);
                    var mxRecordDef = update.defineMXRecordSet(resource.getName())
                            .withMailExchange(firstMx.getExchange(), firstMx.getPreference());
                    for (int i = 1; i < resource.getMxRecords().size(); i++) {
                        var mx = resource.getMxRecords().get(i);
                        mxRecordDef = mxRecordDef.withMailExchange(mx.getExchange(), mx.getPreference());
                    }
                    mxRecordDef.withTimeToLive(ttl).attach();
                }
            }
            case "TXT" -> {
                if (resource.getTxtRecords() != null && !resource.getTxtRecords().isEmpty()) {
                    var firstTxt = resource.getTxtRecords().get(0);
                    var txtRecordDef = update.defineTxtRecordSet(resource.getName())
                            .withText(firstTxt);
                    for (int i = 1; i < resource.getTxtRecords().size(); i++) {
                        txtRecordDef = txtRecordDef.withText(resource.getTxtRecords().get(i));
                    }
                    txtRecordDef.withTimeToLive(ttl).attach();
                }
            }
            case "NS" -> {
                if (resource.getNsRecords() != null && !resource.getNsRecords().isEmpty()) {
                    var firstNs = resource.getNsRecords().get(0);
                    var nsRecordDef = update.defineNSRecordSet(resource.getName())
                            .withNameServer(firstNs);
                    for (int i = 1; i < resource.getNsRecords().size(); i++) {
                        nsRecordDef = nsRecordDef.withNameServer(resource.getNsRecords().get(i));
                    }
                    nsRecordDef.withTimeToLive(ttl).attach();
                }
            }
            case "SRV" -> {
                if (resource.getSrvRecords() != null && !resource.getSrvRecords().isEmpty()) {
                    var firstSrv = resource.getSrvRecords().get(0);
                    var srvRecordDef = update.defineSrvRecordSet(resource.getName())
                            .withRecord(firstSrv.getTarget(), firstSrv.getPort(), firstSrv.getPriority(), firstSrv.getWeight());
                    for (int i = 1; i < resource.getSrvRecords().size(); i++) {
                        var srv = resource.getSrvRecords().get(i);
                        srvRecordDef = srvRecordDef.withRecord(srv.getTarget(), srv.getPort(), srv.getPriority(), srv.getWeight());
                    }
                    srvRecordDef.withTimeToLive(ttl).attach();
                }
            }
            case "CAA" -> {
                if (resource.getCaaRecords() != null && !resource.getCaaRecords().isEmpty()) {
                    var firstCaa = resource.getCaaRecords().get(0);
                    var caaRecordDef = update.defineCaaRecordSet(resource.getName())
                            .withRecord(firstCaa.getFlags(), firstCaa.getTag(), firstCaa.getValue());
                    for (int i = 1; i < resource.getCaaRecords().size(); i++) {
                        var caa = resource.getCaaRecords().get(i);
                        caaRecordDef = caaRecordDef.withRecord(caa.getFlags(), caa.getTag(), caa.getValue());
                    }
                    caaRecordDef.withTimeToLive(ttl).attach();
                }
            }
            case "PTR" -> {
                if (resource.getPtrRecords() != null && !resource.getPtrRecords().isEmpty()) {
                    var firstPtr = resource.getPtrRecords().get(0);
                    var ptrRecordDef = update.definePtrRecordSet(resource.getName())
                            .withTargetDomainName(firstPtr);
                    for (int i = 1; i < resource.getPtrRecords().size(); i++) {
                        ptrRecordDef = ptrRecordDef.withTargetDomainName(resource.getPtrRecords().get(i));
                    }
                    ptrRecordDef.withTimeToLive(ttl).attach();
                }
            }
            default -> throw new RuntimeException("Unsupported record type: " + resource.getType());
        }

        update.apply();
    }

    private DnsRecordSetResource readRecordSet(DnsZone zone, String name, String type) {
        var resource = new DnsRecordSetResource();
        resource.setZoneName(zone.name());
        resource.setResourceGroup(zone.resourceGroupName());
        resource.setName(name);
        resource.setType(type);

        switch (type.toUpperCase()) {
            case "A" -> {
                var recordSets = zone.aRecordSets();
                var recordSet = recordSets.list().stream()
                        .filter(rs -> rs.name().equals(name))
                        .findFirst()
                        .orElse(null);
                if (recordSet == null) return null;
                resource.setTtl(recordSet.timeToLive());
                resource.setARecords(new ArrayList<>(recordSet.ipv4Addresses()));
                resource.setId(recordSet.id());
                resource.setFqdn(recordSet.fqdn());
                resource.setEtag(recordSet.etag());
            }
            case "AAAA" -> {
                var recordSet = zone.aaaaRecordSets().list().stream()
                        .filter(rs -> rs.name().equals(name))
                        .findFirst()
                        .orElse(null);
                if (recordSet == null) return null;
                resource.setTtl(recordSet.timeToLive());
                resource.setAaaaRecords(new ArrayList<>(recordSet.ipv6Addresses()));
                resource.setId(recordSet.id());
                resource.setFqdn(recordSet.fqdn());
                resource.setEtag(recordSet.etag());
            }
            case "CNAME" -> {
                var recordSet = zone.cNameRecordSets().list().stream()
                        .filter(rs -> rs.name().equals(name))
                        .findFirst()
                        .orElse(null);
                if (recordSet == null) return null;
                resource.setTtl(recordSet.timeToLive());
                resource.setCnameRecord(recordSet.canonicalName());
                resource.setId(recordSet.id());
                resource.setFqdn(recordSet.fqdn());
                resource.setEtag(recordSet.etag());
            }
            case "MX" -> {
                var recordSet = zone.mxRecordSets().list().stream()
                        .filter(rs -> rs.name().equals(name))
                        .findFirst()
                        .orElse(null);
                if (recordSet == null) return null;
                resource.setTtl(recordSet.timeToLive());
                resource.setMxRecords(recordSet.records().stream()
                        .map(mx -> DnsRecordSetResource.MxRecord.builder()
                                .preference(mx.preference())
                                .exchange(mx.exchange())
                                .build())
                        .collect(Collectors.toList()));
                resource.setId(recordSet.id());
                resource.setFqdn(recordSet.fqdn());
                resource.setEtag(recordSet.etag());
            }
            case "TXT" -> {
                var recordSet = zone.txtRecordSets().list().stream()
                        .filter(rs -> rs.name().equals(name))
                        .findFirst()
                        .orElse(null);
                if (recordSet == null) return null;
                resource.setTtl(recordSet.timeToLive());
                resource.setTxtRecords(recordSet.records().stream()
                        .flatMap(txt -> txt.value().stream())
                        .collect(Collectors.toList()));
                resource.setId(recordSet.id());
                resource.setFqdn(recordSet.fqdn());
                resource.setEtag(recordSet.etag());
            }
            case "NS" -> {
                var recordSet = zone.nsRecordSets().list().stream()
                        .filter(rs -> rs.name().equals(name))
                        .findFirst()
                        .orElse(null);
                if (recordSet == null) return null;
                resource.setTtl(recordSet.timeToLive());
                resource.setNsRecords(new ArrayList<>(recordSet.nameServers()));
                resource.setId(recordSet.id());
                resource.setFqdn(recordSet.fqdn());
                resource.setEtag(recordSet.etag());
            }
            case "SRV" -> {
                var recordSet = zone.srvRecordSets().list().stream()
                        .filter(rs -> rs.name().equals(name))
                        .findFirst()
                        .orElse(null);
                if (recordSet == null) return null;
                resource.setTtl(recordSet.timeToLive());
                resource.setSrvRecords(recordSet.records().stream()
                        .map(srv -> DnsRecordSetResource.SrvRecord.builder()
                                .priority(srv.priority())
                                .weight(srv.weight())
                                .port(srv.port())
                                .target(srv.target())
                                .build())
                        .collect(Collectors.toList()));
                resource.setId(recordSet.id());
                resource.setFqdn(recordSet.fqdn());
                resource.setEtag(recordSet.etag());
            }
            case "CAA" -> {
                var recordSet = zone.caaRecordSets().list().stream()
                        .filter(rs -> rs.name().equals(name))
                        .findFirst()
                        .orElse(null);
                if (recordSet == null) return null;
                resource.setTtl(recordSet.timeToLive());
                resource.setCaaRecords(recordSet.records().stream()
                        .map(caa -> DnsRecordSetResource.CaaRecord.builder()
                                .flags(caa.flags())
                                .tag(caa.tag())
                                .value(caa.value())
                                .build())
                        .collect(Collectors.toList()));
                resource.setId(recordSet.id());
                resource.setFqdn(recordSet.fqdn());
                resource.setEtag(recordSet.etag());
            }
            case "PTR" -> {
                var recordSet = zone.ptrRecordSets().list().stream()
                        .filter(rs -> rs.name().equals(name))
                        .findFirst()
                        .orElse(null);
                if (recordSet == null) return null;
                resource.setTtl(recordSet.timeToLive());
                resource.setPtrRecords(new ArrayList<>(recordSet.targetDomainNames()));
                resource.setId(recordSet.id());
                resource.setFqdn(recordSet.fqdn());
                resource.setEtag(recordSet.etag());
            }
            default -> {
                return null;
            }
        }

        return resource;
    }
}
