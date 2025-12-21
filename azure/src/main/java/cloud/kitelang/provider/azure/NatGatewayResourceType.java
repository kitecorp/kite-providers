package cloud.kitelang.provider.azure;

import cloud.kitelang.provider.Diagnostic;
import cloud.kitelang.provider.ResourceTypeHandler;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.SubResource;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.network.NetworkManager;
import com.azure.resourcemanager.network.fluent.models.NatGatewayInner;
import com.azure.resourcemanager.network.models.NatGatewaySku;
import com.azure.resourcemanager.network.models.NatGatewaySkuName;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ResourceTypeHandler for Azure NAT Gateway.
 * Implements CRUD operations for NAT Gateways using Azure SDK.
 *
 * Note: Uses lower-level service client as NetworkManager doesn't expose NAT Gateway fluent API.
 */
@Slf4j
public class NatGatewayResourceType extends ResourceTypeHandler<NatGatewayResource> {

    private final NetworkManager networkManager;

    public NatGatewayResourceType() {
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

    public NatGatewayResourceType(NetworkManager networkManager) {
        this.networkManager = networkManager;
    }

    @Override
    public NatGatewayResource create(NatGatewayResource resource) {
        log.info("Creating NAT Gateway '{}' in resource group '{}'",
                resource.getName(), resource.getResourceGroup());

        var natGatewayInner = new NatGatewayInner()
                .withLocation(resource.getLocation())
                .withSku(new NatGatewaySku().withName(NatGatewaySkuName.STANDARD));

        // Set idle timeout
        if (resource.getIdleTimeoutInMinutes() != null) {
            natGatewayInner.withIdleTimeoutInMinutes(resource.getIdleTimeoutInMinutes());
        }

        // Add public IP addresses
        if (resource.getPublicIpAddressIds() != null && !resource.getPublicIpAddressIds().isEmpty()) {
            var publicIps = resource.getPublicIpAddressIds().stream()
                    .map(id -> new SubResource().withId(id))
                    .collect(Collectors.toList());
            natGatewayInner.withPublicIpAddresses(publicIps);
        }

        // Add public IP prefixes
        if (resource.getPublicIpPrefixIds() != null && !resource.getPublicIpPrefixIds().isEmpty()) {
            var prefixes = resource.getPublicIpPrefixIds().stream()
                    .map(id -> new SubResource().withId(id))
                    .collect(Collectors.toList());
            natGatewayInner.withPublicIpPrefixes(prefixes);
        }

        // Add tags
        if (resource.getTags() != null && !resource.getTags().isEmpty()) {
            natGatewayInner.withTags(resource.getTags());
        }

        var result = networkManager.serviceClient().getNatGateways()
                .createOrUpdate(resource.getResourceGroup(), resource.getName(), natGatewayInner);

        log.info("Created NAT Gateway: {}", result.id());

        return mapToResource(result, resource.getResourceGroup());
    }

    @Override
    public NatGatewayResource read(NatGatewayResource resource) {
        if (resource.getName() == null || resource.getResourceGroup() == null) {
            log.warn("Cannot read NAT Gateway without name and resourceGroup");
            return null;
        }

        log.info("Reading NAT Gateway '{}' in resource group '{}'",
                resource.getName(), resource.getResourceGroup());

        try {
            var result = networkManager.serviceClient().getNatGateways()
                    .getByResourceGroup(resource.getResourceGroup(), resource.getName());

            if (result == null) {
                return null;
            }

            return mapToResource(result, resource.getResourceGroup());

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("ResourceNotFound")) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public NatGatewayResource update(NatGatewayResource resource) {
        log.info("Updating NAT Gateway '{}' in resource group '{}'",
                resource.getName(), resource.getResourceGroup());

        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("NAT Gateway not found: " + resource.getName());
        }

        var natGatewayInner = new NatGatewayInner()
                .withLocation(resource.getLocation())
                .withSku(new NatGatewaySku().withName(NatGatewaySkuName.STANDARD));

        // Update idle timeout
        if (resource.getIdleTimeoutInMinutes() != null) {
            natGatewayInner.withIdleTimeoutInMinutes(resource.getIdleTimeoutInMinutes());
        }

        // Update public IP addresses
        if (resource.getPublicIpAddressIds() != null && !resource.getPublicIpAddressIds().isEmpty()) {
            var publicIps = resource.getPublicIpAddressIds().stream()
                    .map(id -> new SubResource().withId(id))
                    .collect(Collectors.toList());
            natGatewayInner.withPublicIpAddresses(publicIps);
        }

        // Update public IP prefixes
        if (resource.getPublicIpPrefixIds() != null && !resource.getPublicIpPrefixIds().isEmpty()) {
            var prefixes = resource.getPublicIpPrefixIds().stream()
                    .map(id -> new SubResource().withId(id))
                    .collect(Collectors.toList());
            natGatewayInner.withPublicIpPrefixes(prefixes);
        }

        // Update tags
        if (resource.getTags() != null) {
            natGatewayInner.withTags(resource.getTags());
        }

        var result = networkManager.serviceClient().getNatGateways()
                .createOrUpdate(resource.getResourceGroup(), resource.getName(), natGatewayInner);

        return mapToResource(result, resource.getResourceGroup());
    }

    @Override
    public boolean delete(NatGatewayResource resource) {
        if (resource.getName() == null || resource.getResourceGroup() == null) {
            log.warn("Cannot delete NAT Gateway without name and resourceGroup");
            return false;
        }

        log.info("Deleting NAT Gateway '{}' from resource group '{}'",
                resource.getName(), resource.getResourceGroup());

        try {
            networkManager.serviceClient().getNatGateways()
                    .delete(resource.getResourceGroup(), resource.getName());

            log.info("Deleted NAT Gateway: {}", resource.getName());
            return true;

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("ResourceNotFound")) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public List<Diagnostic> validate(NatGatewayResource resource) {
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

        // Validate idle timeout
        if (resource.getIdleTimeoutInMinutes() != null) {
            if (resource.getIdleTimeoutInMinutes() < 4 || resource.getIdleTimeoutInMinutes() > 120) {
                diagnostics.add(Diagnostic.error("idleTimeoutInMinutes must be between 4 and 120")
                        .withProperty("idleTimeoutInMinutes"));
            }
        }

        return diagnostics;
    }

    private NatGatewayResource mapToResource(NatGatewayInner nat, String resourceGroup) {
        var resource = new NatGatewayResource();

        // Input properties
        resource.setName(nat.name());
        resource.setResourceGroup(resourceGroup);
        resource.setLocation(nat.location());
        resource.setIdleTimeoutInMinutes(nat.idleTimeoutInMinutes());

        // SKU
        if (nat.sku() != null && nat.sku().name() != null) {
            resource.setSkuName(nat.sku().name().toString());
        }

        // Public IP addresses
        if (nat.publicIpAddresses() != null && !nat.publicIpAddresses().isEmpty()) {
            resource.setPublicIpAddressIds(nat.publicIpAddresses().stream()
                    .map(SubResource::id)
                    .collect(Collectors.toList()));
        }

        // Public IP prefixes
        if (nat.publicIpPrefixes() != null && !nat.publicIpPrefixes().isEmpty()) {
            resource.setPublicIpPrefixIds(nat.publicIpPrefixes().stream()
                    .map(SubResource::id)
                    .collect(Collectors.toList()));
        }

        // Tags
        if (nat.tags() != null && !nat.tags().isEmpty()) {
            resource.setTags(new HashMap<>(nat.tags()));
        }

        // Cloud-managed properties
        resource.setId(nat.id());

        if (nat.provisioningState() != null) {
            resource.setProvisioningState(nat.provisioningState().toString());
        }

        resource.setResourceGuid(nat.resourceGuid());

        // Get associated subnet IDs
        if (nat.subnets() != null && !nat.subnets().isEmpty()) {
            resource.setSubnetIds(nat.subnets().stream()
                    .map(SubResource::id)
                    .collect(Collectors.toList()));
        }

        return resource;
    }
}
