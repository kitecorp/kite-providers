package cloud.kitelang.provider.gcp.dns;

import cloud.kitelang.provider.Diagnostic;
import cloud.kitelang.provider.ResourceTypeHandler;
import cloud.kitelang.provider.gcp.GcpClientAware;
import com.google.cloud.compute.v1.ForwardingRulesClient;
import com.google.cloud.compute.v1.InstancesClient;
import com.google.cloud.compute.v1.NetworksClient;
import com.google.cloud.compute.v1.SubnetworksClient;
import com.google.cloud.dns.ChangeRequestInfo;
import com.google.cloud.dns.Dns;
import com.google.cloud.dns.DnsOptions;
import com.google.cloud.dns.RecordSet;
import com.google.cloud.storage.Storage;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * ResourceTypeHandler for GCP Cloud DNS Resource Record Set.
 * Implements CRUD operations using the GCP Cloud DNS Java SDK.
 */
@Slf4j
public class ResourceRecordSetResourceType extends ResourceTypeHandler<ResourceRecordSetResource> implements GcpClientAware {

    private volatile Dns dnsClient;

    public ResourceRecordSetResourceType() {
        // Client created lazily to pick up configuration
    }

    /** Constructor for testing with a mock client. */
    public ResourceRecordSetResourceType(Dns dnsClient) {
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
    public ResourceRecordSetResource create(ResourceRecordSetResource resource) {
        log.info("Creating DNS Record '{}' of type '{}' in zone '{}'",
                resource.getName(), resource.getType(), resource.getManagedZone());

        var recordSet = buildRecordSet(resource);

        var changeRequest = ChangeRequestInfo.newBuilder()
                .add(recordSet)
                .build();

        getClient().applyChangeRequest(resource.getManagedZone(), changeRequest);
        log.info("Created DNS Record: {} {}", resource.getName(), resource.getType());

        return read(resource);
    }

    @Override
    public ResourceRecordSetResource read(ResourceRecordSetResource resource) {
        if (resource.getManagedZone() == null || resource.getName() == null || resource.getType() == null) {
            log.warn("Cannot read DNS Record without managedZone, name, and type");
            return null;
        }

        log.info("Reading DNS Record: {} {} in zone: {}",
                resource.getName(), resource.getType(), resource.getManagedZone());

        var recordSets = getClient().listRecordSets(resource.getManagedZone(),
                Dns.RecordSetListOption.dnsName(resource.getName()),
                Dns.RecordSetListOption.type(RecordSet.Type.valueOf(resource.getType())));

        for (var recordSet : recordSets.iterateAll()) {
            if (recordSet.getName().equals(resource.getName()) &&
                recordSet.getType().name().equals(resource.getType())) {
                return mapToResource(recordSet, resource.getManagedZone());
            }
        }

        return null;
    }

    @Override
    public ResourceRecordSetResource update(ResourceRecordSetResource resource) {
        log.info("Updating DNS Record '{}' of type '{}' in zone '{}'",
                resource.getName(), resource.getType(), resource.getManagedZone());

        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("DNS Record not found: " + resource.getName() + " " + resource.getType());
        }

        // Cloud DNS requires delete + add to update a record set
        var oldRecordSet = buildRecordSet(current);
        var newRecordSet = buildRecordSet(resource);

        var changeRequest = ChangeRequestInfo.newBuilder()
                .delete(oldRecordSet)
                .add(newRecordSet)
                .build();

        getClient().applyChangeRequest(resource.getManagedZone(), changeRequest);
        log.info("Updated DNS Record: {} {}", resource.getName(), resource.getType());

        return read(resource);
    }

    @Override
    public boolean delete(ResourceRecordSetResource resource) {
        if (resource.getManagedZone() == null || resource.getName() == null || resource.getType() == null) {
            log.warn("Cannot delete DNS Record without managedZone, name, and type");
            return false;
        }

        log.info("Deleting DNS Record '{}' of type '{}' from zone '{}'",
                resource.getName(), resource.getType(), resource.getManagedZone());

        var current = read(resource);
        if (current == null) {
            return false;
        }

        var recordSet = buildRecordSet(current);
        var changeRequest = ChangeRequestInfo.newBuilder()
                .delete(recordSet)
                .build();

        getClient().applyChangeRequest(resource.getManagedZone(), changeRequest);
        log.info("Deleted DNS Record: {} {}", resource.getName(), resource.getType());
        return true;
    }

    @Override
    public List<Diagnostic> validate(ResourceRecordSetResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getManagedZone() == null || resource.getManagedZone().isBlank()) {
            diagnostics.add(Diagnostic.error("managedZone is required").withProperty("managedZone"));
        }
        if (resource.getName() == null || resource.getName().isBlank()) {
            diagnostics.add(Diagnostic.error("name is required").withProperty("name"));
        } else if (!resource.getName().endsWith(".")) {
            diagnostics.add(Diagnostic.error("name must be a fully qualified domain name ending with a dot")
                    .withProperty("name"));
        }
        if (resource.getType() == null || resource.getType().isBlank()) {
            diagnostics.add(Diagnostic.error("type is required").withProperty("type"));
        }
        if (resource.getRrdatas() == null || resource.getRrdatas().isEmpty()) {
            diagnostics.add(Diagnostic.error("rrdatas must contain at least one value").withProperty("rrdatas"));
        }

        return diagnostics;
    }

    /**
     * Build a Cloud DNS RecordSet from our resource model.
     */
    private RecordSet buildRecordSet(ResourceRecordSetResource resource) {
        var builder = RecordSet.newBuilder(resource.getName(), RecordSet.Type.valueOf(resource.getType()));

        if (resource.getTtl() != null) {
            builder.setTtl(resource.getTtl(), TimeUnit.SECONDS);
        }

        if (resource.getRrdatas() != null) {
            builder.setRecords(resource.getRrdatas());
        }

        return builder.build();
    }

    private ResourceRecordSetResource mapToResource(RecordSet recordSet, String managedZone) {
        var resource = new ResourceRecordSetResource();

        resource.setManagedZone(managedZone);
        resource.setName(recordSet.getName());
        resource.setType(recordSet.getType().name());
        resource.setTtl((int) recordSet.getTtl());
        resource.setRrdatas(new ArrayList<>(recordSet.getRecords()));

        return resource;
    }
}
