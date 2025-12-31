package cloud.kitelang.provider.azure.networking;

import cloud.kitelang.provider.Diagnostic;
import cloud.kitelang.provider.ResourceTypeHandler;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.Region;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.network.NetworkManager;
import com.azure.resourcemanager.network.models.IpAllocationMethod;
import com.azure.resourcemanager.network.models.IpVersion;
import com.azure.resourcemanager.network.models.PublicIPSkuType;
import com.azure.resourcemanager.network.models.PublicIpAddress;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * ResourceTypeHandler for Azure Public IP Address.
 * Implements CRUD operations for Public IPs using Azure SDK.
 */
@Slf4j
public class PublicIpAddressResourceType extends ResourceTypeHandler<PublicIpAddressResource> {

    private final NetworkManager networkManager;

    public PublicIpAddressResourceType() {
        var credential = new DefaultAzureCredentialBuilder().build();

        String subscriptionId = System.getenv("AZURE_SUBSCRIPTION_ID");
        if (subscriptionId == null || subscriptionId.isBlank()) {
            throw new IllegalStateException(
                    "AZURE_SUBSCRIPTION_ID environment variable must be set");
        }

        String tenantId = System.getenv("AZURE_TENANT_ID");
        var profile = new AzureProfile(tenantId, subscriptionId, AzureEnvironment.AZURE);

        this.networkManager = NetworkManager.authenticate(credential, profile);
    }

    public PublicIpAddressResourceType(NetworkManager networkManager) {
        this.networkManager = networkManager;
    }

    @Override
    public PublicIpAddressResource create(PublicIpAddressResource resource) {
        log.info("Creating Public IP '{}' in resource group '{}'",
                resource.getName(), resource.getResourceGroup());

        var definition = networkManager.publicIpAddresses()
                .define(resource.getName())
                .withRegion(Region.fromName(resource.getLocation()))
                .withExistingResourceGroup(resource.getResourceGroup());

        // Set SKU
        String sku = resource.getSku();
        if (sku != null && sku.equalsIgnoreCase("Standard")) {
            definition = definition.withSku(PublicIPSkuType.STANDARD);
        } else {
            definition = definition.withSku(PublicIPSkuType.BASIC);
        }

        // Set allocation method
        String allocationMethod = resource.getAllocationMethod();
        if (allocationMethod != null && allocationMethod.equalsIgnoreCase("Static")) {
            definition = definition.withStaticIP();
        } else {
            definition = definition.withDynamicIP();
        }

        // Set IP version
        if (resource.getIpVersion() != null && resource.getIpVersion().equalsIgnoreCase("IPv6")) {
            definition = definition.withIpAddressVersion(IpVersion.IPV6);
        }

        // Set idle timeout
        if (resource.getIdleTimeoutInMinutes() != null) {
            definition = definition.withIdleTimeoutInMinutes(resource.getIdleTimeoutInMinutes());
        }

        // Set domain name label
        if (resource.getDomainNameLabel() != null && !resource.getDomainNameLabel().isBlank()) {
            definition = definition.withLeafDomainLabel(resource.getDomainNameLabel());
        }

        // Set availability zones
        if (resource.getZones() != null && !resource.getZones().isEmpty()) {
            // Zones require Standard SKU
            for (String zone : resource.getZones()) {
                definition = definition.withAvailabilityZone(
                        com.azure.resourcemanager.resources.fluentcore.arm.AvailabilityZoneId.fromString(zone));
            }
        }

        // Set tags
        if (resource.getTags() != null && !resource.getTags().isEmpty()) {
            definition = definition.withTags(resource.getTags());
        }

        var publicIp = definition.create();
        log.info("Created Public IP: {}", publicIp.id());

        return mapToResource(publicIp);
    }

    @Override
    public PublicIpAddressResource read(PublicIpAddressResource resource) {
        if (resource.getName() == null || resource.getResourceGroup() == null) {
            log.warn("Cannot read Public IP without name and resourceGroup");
            return null;
        }

        log.info("Reading Public IP '{}' in resource group '{}'",
                resource.getName(), resource.getResourceGroup());

        try {
            var publicIp = networkManager.publicIpAddresses()
                    .getByResourceGroup(resource.getResourceGroup(), resource.getName());

            if (publicIp == null) {
                return null;
            }

            return mapToResource(publicIp);

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("ResourceNotFound")) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public PublicIpAddressResource update(PublicIpAddressResource resource) {
        log.info("Updating Public IP '{}' in resource group '{}'",
                resource.getName(), resource.getResourceGroup());

        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("Public IP not found: " + resource.getName());
        }

        var publicIp = networkManager.publicIpAddresses()
                .getByResourceGroup(resource.getResourceGroup(), resource.getName());

        var update = publicIp.update();

        // Update idle timeout
        if (resource.getIdleTimeoutInMinutes() != null) {
            update = update.withIdleTimeoutInMinutes(resource.getIdleTimeoutInMinutes());
        }

        // Update domain name label
        if (resource.getDomainNameLabel() != null) {
            if (resource.getDomainNameLabel().isBlank()) {
                update = update.withoutLeafDomainLabel();
            } else {
                update = update.withLeafDomainLabel(resource.getDomainNameLabel());
            }
        }

        // Update tags
        if (resource.getTags() != null) {
            update = update.withTags(resource.getTags());
        }

        var updated = update.apply();
        return mapToResource(updated);
    }

    @Override
    public boolean delete(PublicIpAddressResource resource) {
        if (resource.getName() == null || resource.getResourceGroup() == null) {
            log.warn("Cannot delete Public IP without name and resourceGroup");
            return false;
        }

        log.info("Deleting Public IP '{}' from resource group '{}'",
                resource.getName(), resource.getResourceGroup());

        try {
            networkManager.publicIpAddresses()
                    .deleteByResourceGroup(resource.getResourceGroup(), resource.getName());

            log.info("Deleted Public IP: {}", resource.getName());
            return true;

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("ResourceNotFound")) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public List<Diagnostic> validate(PublicIpAddressResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getName() == null || resource.getName().isBlank()) {
            diagnostics.add(Diagnostic.error("name is required")
                    .withProperty("name"));
        } else {
            if (resource.getName().length() < 1 || resource.getName().length() > 80) {
                diagnostics.add(Diagnostic.error("name must be between 1 and 80 characters")
                        .withProperty("name"));
            }
        }

        if (resource.getResourceGroup() == null || resource.getResourceGroup().isBlank()) {
            diagnostics.add(Diagnostic.error("resourceGroup is required")
                    .withProperty("resourceGroup"));
        }

        if (resource.getLocation() == null || resource.getLocation().isBlank()) {
            diagnostics.add(Diagnostic.error("location is required")
                    .withProperty("location"));
        }

        // Validate SKU
        String sku = resource.getSku();
        if (sku != null && !sku.isBlank()) {
            if (!sku.equalsIgnoreCase("Basic") && !sku.equalsIgnoreCase("Standard")) {
                diagnostics.add(Diagnostic.error("sku must be 'Basic' or 'Standard'")
                        .withProperty("sku"));
            }
        }

        // Validate allocation method
        String allocationMethod = resource.getAllocationMethod();
        if (allocationMethod != null && !allocationMethod.isBlank()) {
            if (!allocationMethod.equalsIgnoreCase("Static") &&
                !allocationMethod.equalsIgnoreCase("Dynamic")) {
                diagnostics.add(Diagnostic.error("allocationMethod must be 'Static' or 'Dynamic'")
                        .withProperty("allocationMethod"));
            }
        }

        // Standard SKU requires Static allocation
        if (sku != null && sku.equalsIgnoreCase("Standard") &&
            allocationMethod != null && allocationMethod.equalsIgnoreCase("Dynamic")) {
            diagnostics.add(Diagnostic.error("Standard SKU requires Static allocation method")
                    .withProperty("allocationMethod"));
        }

        // Validate idle timeout
        if (resource.getIdleTimeoutInMinutes() != null) {
            if (resource.getIdleTimeoutInMinutes() < 4 || resource.getIdleTimeoutInMinutes() > 30) {
                diagnostics.add(Diagnostic.error("idleTimeoutInMinutes must be between 4 and 30")
                        .withProperty("idleTimeoutInMinutes"));
            }
        }

        // Zones require Standard SKU
        if (resource.getZones() != null && !resource.getZones().isEmpty()) {
            if (sku == null || !sku.equalsIgnoreCase("Standard")) {
                diagnostics.add(Diagnostic.error("availability zones require Standard SKU")
                        .withProperty("zones"));
            }
        }

        return diagnostics;
    }

    private PublicIpAddressResource mapToResource(PublicIpAddress pip) {
        var resource = new PublicIpAddressResource();

        // Input properties
        resource.setName(pip.name());
        resource.setResourceGroup(pip.resourceGroupName());
        resource.setLocation(pip.regionName());

        if (pip.sku() != null) {
            resource.setSku(pip.sku().toString());
        }

        if (pip.ipAllocationMethod() != null) {
            resource.setAllocationMethod(pip.ipAllocationMethod().toString());
        }

        if (pip.version() != null) {
            resource.setIpVersion(pip.version().toString());
        }

        resource.setIdleTimeoutInMinutes(pip.idleTimeoutInMinutes());
        resource.setDomainNameLabel(pip.leafDomainLabel());

        if (pip.availabilityZones() != null && !pip.availabilityZones().isEmpty()) {
            List<String> zones = new ArrayList<>();
            for (var zone : pip.availabilityZones()) {
                zones.add(zone.toString());
            }
            resource.setZones(zones);
        }

        // Tags
        if (pip.tags() != null && !pip.tags().isEmpty()) {
            resource.setTags(new HashMap<>(pip.tags()));
        }

        // Cloud-managed properties
        resource.setId(pip.id());
        resource.setIpAddress(pip.ipAddress());

        if (pip.innerModel().provisioningState() != null) {
            resource.setProvisioningState(pip.innerModel().provisioningState().toString());
        }

        resource.setResourceGuid(pip.innerModel().resourceGuid());
        resource.setFqdn(pip.fqdn());

        return resource;
    }
}
