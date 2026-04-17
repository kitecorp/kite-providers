package cloud.kitelang.provider.azure.dns;

import cloud.kitelang.provider.Diagnostic;
import cloud.kitelang.provider.ResourceTypeHandler;
import cloud.kitelang.provider.azure.AzureClientAware;
import cloud.kitelang.provider.azure.AzureManagers;
import com.azure.resourcemanager.dns.DnsZoneManager;
import com.azure.resourcemanager.dns.models.DnsZone;
import com.azure.resourcemanager.dns.models.ZoneType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * ResourceTypeHandler for Azure DNS Zone.
 * Implements CRUD operations using Azure DNS SDK.
 *
 * Note: Private DNS zones in Azure are managed through a separate service
 * (Private DNS Zones) and not through the standard DNS Zone API.
 * This handler supports public DNS zones.
 */
@Slf4j
public class DnsZoneResourceType extends ResourceTypeHandler<DnsZoneResource> implements AzureClientAware {

    private DnsZoneManager dnsManager;

    public DnsZoneResourceType() {
        // Manager injected by AzureProvider.configure() at runtime.
    }

    /** For tests: inject a pre-authenticated manager. */
    public DnsZoneResourceType(DnsZoneManager dnsManager) {
        this.dnsManager = dnsManager;
    }

    @Override
    public void setAzureManagers(AzureManagers managers) {
        this.dnsManager = managers.dns();
    }

    @Override
    public DnsZoneResource create(DnsZoneResource resource) {
        log.info("Creating DNS Zone: {} in {}", resource.getName(), resource.getResourceGroup());

        var definition = dnsManager.zones()
                .define(resource.getName())
                .withExistingResourceGroup(resource.getResourceGroup());

        // Add tags
        if (resource.getTags() != null) {
            definition = definition.withTags(resource.getTags());
        }

        var zone = definition.create();

        log.info("Created DNS Zone: {}", zone.id());

        resource.setId(zone.id());
        return read(resource);
    }

    @Override
    public DnsZoneResource read(DnsZoneResource resource) {
        if (resource.getId() == null && (resource.getName() == null || resource.getResourceGroup() == null)) {
            log.warn("Cannot read DNS Zone without id or name/resourceGroup");
            return null;
        }

        log.info("Reading DNS Zone: {}", resource.getId() != null ? resource.getId() : resource.getName());

        try {
            DnsZone zone;
            if (resource.getId() != null) {
                zone = dnsManager.zones().getById(resource.getId());
            } else {
                zone = dnsManager.zones().getByResourceGroup(resource.getResourceGroup(), resource.getName());
            }

            if (zone == null) {
                return null;
            }

            return mapDnsZoneToResource(zone);

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("ResourceNotFound")) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public DnsZoneResource update(DnsZoneResource resource) {
        log.info("Updating DNS Zone: {}", resource.getId());

        var zone = dnsManager.zones().getById(resource.getId());
        if (zone == null) {
            throw new RuntimeException("DNS Zone not found: " + resource.getName());
        }

        var update = zone.update();

        // Update tags
        if (resource.getTags() != null) {
            update = update.withTags(resource.getTags());
        }

        zone = update.apply();

        return read(resource);
    }

    @Override
    public boolean delete(DnsZoneResource resource) {
        if (resource.getId() == null) {
            log.warn("Cannot delete DNS Zone without id");
            return false;
        }

        log.info("Deleting DNS Zone: {}", resource.getId());

        try {
            dnsManager.zones().deleteById(resource.getId());
            log.info("Deleted DNS Zone: {}", resource.getId());
            return true;

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("ResourceNotFound")) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public List<Diagnostic> validate(DnsZoneResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getName() == null || resource.getName().isBlank()) {
            diagnostics.add(Diagnostic.error("name is required")
                    .withProperty("name"));
        } else {
            // Validate domain name format
            if (!resource.getName().matches("^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)*\\.?$")) {
                diagnostics.add(Diagnostic.error("Invalid domain name format")
                        .withProperty("name"));
            }
        }

        if (resource.getResourceGroup() == null || resource.getResourceGroup().isBlank()) {
            diagnostics.add(Diagnostic.error("resourceGroup is required")
                    .withProperty("resourceGroup"));
        }

        // Private zones not supported through this API
        if ("Private".equalsIgnoreCase(resource.getZoneType())) {
            diagnostics.add(Diagnostic.error("Private DNS zones require Azure Private DNS Zone service",
                    "Use zoneType: Public or omit for public DNS zones")
                    .withProperty("zoneType"));
        }

        return diagnostics;
    }

    private DnsZoneResource mapDnsZoneToResource(DnsZone zone) {
        var resource = new DnsZoneResource();

        // Input properties
        resource.setName(zone.name());
        resource.setResourceGroup(zone.resourceGroupName());
        resource.setZoneType("Public");
        resource.setTags(zone.tags());

        // Cloud-managed properties
        resource.setId(zone.id());
        resource.setNameServers(new ArrayList<>(zone.nameServers()));
        resource.setNumberOfRecordSets(zone.numberOfRecordSets());
        resource.setMaxNumberOfRecordSets(zone.maxNumberOfRecordSets());
        resource.setEtag(zone.etag());

        return resource;
    }
}
