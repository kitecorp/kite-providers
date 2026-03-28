package cloud.kitelang.provider.gcp.dns;

import cloud.kitelang.provider.Diagnostic;
import cloud.kitelang.provider.ResourceTypeHandler;
import cloud.kitelang.provider.gcp.GcpClientAware;
import com.google.cloud.compute.v1.ForwardingRulesClient;
import com.google.cloud.compute.v1.InstancesClient;
import com.google.cloud.compute.v1.NetworksClient;
import com.google.cloud.compute.v1.SubnetworksClient;
import com.google.cloud.dns.Dns;
import com.google.cloud.dns.DnsOptions;
import com.google.cloud.dns.Zone;
import com.google.cloud.dns.ZoneInfo;
import com.google.cloud.storage.Storage;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ResourceTypeHandler for GCP Cloud DNS Managed Zone.
 * Implements CRUD operations using the GCP Cloud DNS Java SDK.
 */
@Slf4j
public class ManagedZoneResourceType extends ResourceTypeHandler<ManagedZoneResource> implements GcpClientAware {

    private volatile Dns dnsClient;

    public ManagedZoneResourceType() {
        // Client created lazily to pick up configuration
    }

    /** Constructor for testing with a mock client. */
    public ManagedZoneResourceType(Dns dnsClient) {
        this.dnsClient = dnsClient;
    }

    @Override
    public void setGcpClients(InstancesClient instancesClient,
                              NetworksClient networksClient,
                              SubnetworksClient subnetworksClient,
                              ForwardingRulesClient forwardingRulesClient,
                              Storage storage,
                              Dns dns) {
        this.dnsClient = dns;
    }

    private Dns getClient() {
        if (dnsClient == null) {
            synchronized (this) {
                if (dnsClient == null) {
                    log.debug("Creating DNS client with default configuration");
                    dnsClient = DnsOptions.getDefaultInstance().getService();
                }
            }
        }
        return dnsClient;
    }

    @Override
    public ManagedZoneResource create(ManagedZoneResource resource) {
        log.info("Creating Managed Zone: {} for DNS name: {}", resource.getName(), resource.getDnsName());

        var builder = ZoneInfo.newBuilder(resource.getName())
                .setDnsName(resource.getDnsName())
                .setDescription(resource.getDescription() != null ? resource.getDescription() : "");

        if (resource.getLabels() != null && !resource.getLabels().isEmpty()) {
            builder.setLabels(resource.getLabels());
        }

        var zone = getClient().create(builder.build());
        log.info("Created Managed Zone: {}", zone.getName());

        return read(resource);
    }

    @Override
    public ManagedZoneResource read(ManagedZoneResource resource) {
        if (resource.getName() == null) {
            log.warn("Cannot read Managed Zone without name");
            return null;
        }

        log.info("Reading Managed Zone: {}", resource.getName());

        var zone = getClient().getZone(resource.getName());
        if (zone == null) {
            return null;
        }

        return mapToResource(zone);
    }

    @Override
    public ManagedZoneResource update(ManagedZoneResource resource) {
        log.info("Updating Managed Zone: {}", resource.getName());

        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("Managed Zone not found: " + resource.getName());
        }

        // GCP Cloud DNS managed zones are largely immutable after creation.
        // The DNS name, name, and visibility cannot be changed.
        // Description and labels updates require the REST API which is not exposed
        // by the higher-level Java client library.
        // For now, we log that the zone exists and return its current state.
        log.info("Managed Zone '{}' exists. DNS name, description, and labels cannot be " +
                "updated via the Cloud DNS Java SDK after creation.", resource.getName());

        return current;
    }

    @Override
    public boolean delete(ManagedZoneResource resource) {
        if (resource.getName() == null) {
            log.warn("Cannot delete Managed Zone without name");
            return false;
        }

        log.info("Deleting Managed Zone: {}", resource.getName());

        // Must delete all non-NS/SOA records before deleting the zone
        var zone = getClient().getZone(resource.getName());
        if (zone == null) {
            return false;
        }

        // Delete all user-created records first
        deleteUserRecords(resource.getName());

        var deleted = getClient().delete(resource.getName());
        if (deleted) {
            log.info("Deleted Managed Zone: {}", resource.getName());
        }
        return deleted;
    }

    @Override
    public List<Diagnostic> validate(ManagedZoneResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getName() == null || resource.getName().isBlank()) {
            diagnostics.add(Diagnostic.error("name is required").withProperty("name"));
        }
        if (resource.getDnsName() == null || resource.getDnsName().isBlank()) {
            diagnostics.add(Diagnostic.error("dnsName is required").withProperty("dnsName"));
        } else if (!resource.getDnsName().endsWith(".")) {
            diagnostics.add(Diagnostic.error("dnsName must end with a trailing dot (e.g., 'example.com.')")
                    .withProperty("dnsName"));
        }

        return diagnostics;
    }

    /**
     * Delete all user-created records (non-NS/SOA) from a managed zone.
     * Required before a zone can be deleted.
     */
    private void deleteUserRecords(String zoneName) {
        var recordSets = getClient().listRecordSets(zoneName);
        var changeBuilder = com.google.cloud.dns.ChangeRequestInfo.newBuilder();
        var hasRecords = false;

        for (var recordSet : recordSets.iterateAll()) {
            var type = recordSet.getType();
            // NS and SOA records at the zone apex are managed by GCP and cannot be deleted
            if (!"NS".equals(type.name()) && !"SOA".equals(type.name())) {
                changeBuilder.delete(recordSet);
                hasRecords = true;
            }
        }

        if (hasRecords) {
            getClient().applyChangeRequest(zoneName, changeBuilder.build());
        }
    }

    private ManagedZoneResource mapToResource(Zone zone) {
        var resource = new ManagedZoneResource();

        resource.setName(zone.getName());
        resource.setDnsName(zone.getDnsName());
        resource.setDescription(zone.getDescription());

        if (zone.getLabels() != null && !zone.getLabels().isEmpty()) {
            resource.setLabels(new HashMap<>(zone.getLabels()));
        }

        // Cloud-managed properties
        resource.setManagedZoneId(zone.getGeneratedId());
        resource.setNameServers(zone.getNameServers() != null
                ? new ArrayList<>(zone.getNameServers())
                : List.of());
        if (zone.getCreationTimeMillis() != null) {
            resource.setCreationTime(String.valueOf(zone.getCreationTimeMillis()));
        }

        return resource;
    }
}
