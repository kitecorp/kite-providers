package cloud.kitelang.provider.azure.networking;

import cloud.kitelang.provider.Diagnostic;
import cloud.kitelang.provider.ResourceTypeHandler;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.Region;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.network.NetworkManager;
import com.azure.resourcemanager.network.models.Network;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * ResourceTypeHandler for Azure Virtual Network (VNet).
 * Implements CRUD operations for VNets using Azure SDK.
 */
@Slf4j
public class VnetResourceType extends ResourceTypeHandler<VnetResource> {

    private final NetworkManager networkManager;

    public VnetResourceType() {
        // Use DefaultAzureCredential which supports multiple auth methods:
        // - Environment variables (AZURE_CLIENT_ID, AZURE_CLIENT_SECRET, AZURE_TENANT_ID)
        // - Managed Identity
        // - Azure CLI
        // - Visual Studio Code
        var credential = new DefaultAzureCredentialBuilder().build();

        // Get subscription ID from environment
        String subscriptionId = System.getenv("AZURE_SUBSCRIPTION_ID");
        if (subscriptionId == null || subscriptionId.isBlank()) {
            throw new IllegalStateException(
                    "AZURE_SUBSCRIPTION_ID environment variable must be set");
        }

        // Get tenant ID (optional, can be null for most cases)
        String tenantId = System.getenv("AZURE_TENANT_ID");

        // Create Azure profile
        var profile = new AzureProfile(tenantId, subscriptionId, AzureEnvironment.AZURE);

        this.networkManager = NetworkManager.authenticate(credential, profile);
    }

    public VnetResourceType(NetworkManager networkManager) {
        this.networkManager = networkManager;
    }

    @Override
    public VnetResource create(VnetResource resource) {
        log.info("Creating VNet '{}' in resource group '{}' at '{}'",
                resource.getName(), resource.getResourceGroup(), resource.getLocation());

        // Start building the VNet definition
        Network.DefinitionStages.WithCreate definition = networkManager.networks()
                .define(resource.getName())
                .withRegion(Region.fromName(resource.getLocation()))
                .withExistingResourceGroup(resource.getResourceGroup())
                .withAddressSpace(resource.getAddressSpaces().get(0));

        // Add additional address spaces
        for (int i = 1; i < resource.getAddressSpaces().size(); i++) {
            definition = definition.withAddressSpace(resource.getAddressSpaces().get(i));
        }

        // Add DNS servers if specified
        if (resource.getDnsServers() != null && !resource.getDnsServers().isEmpty()) {
            for (String dnsServer : resource.getDnsServers()) {
                definition = definition.withDnsServer(dnsServer);
            }
        }

        // Add tags if specified
        if (resource.getTags() != null && !resource.getTags().isEmpty()) {
            definition = definition.withTags(resource.getTags());
        }

        // Create the VNet
        Network vnet = definition.create();
        log.info("Created VNet: {}", vnet.id());

        return mapNetworkToResource(vnet);
    }

    @Override
    public VnetResource read(VnetResource resource) {
        if (resource.getId() == null && (resource.getName() == null || resource.getResourceGroup() == null)) {
            log.warn("Cannot read VNet without id or (name and resourceGroup)");
            return null;
        }

        log.info("Reading VNet '{}' in resource group '{}'",
                resource.getName(), resource.getResourceGroup());

        try {
            Network vnet;
            if (resource.getId() != null) {
                vnet = networkManager.networks().getById(resource.getId());
            } else {
                vnet = networkManager.networks()
                        .getByResourceGroup(resource.getResourceGroup(), resource.getName());
            }

            if (vnet == null) {
                return null;
            }

            return mapNetworkToResource(vnet);

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("ResourceNotFound")) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public VnetResource update(VnetResource resource) {
        log.info("Updating VNet '{}' in resource group '{}'",
                resource.getName(), resource.getResourceGroup());

        // Read current state
        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("VNet not found: " + resource.getName());
        }

        Network vnet = networkManager.networks()
                .getByResourceGroup(resource.getResourceGroup(), resource.getName());

        var update = vnet.update();

        // Update address spaces
        // Note: Azure doesn't allow removing address spaces that have subnets
        if (resource.getAddressSpaces() != null) {
            for (String addressSpace : resource.getAddressSpaces()) {
                if (!vnet.addressSpaces().contains(addressSpace)) {
                    update = update.withAddressSpace(addressSpace);
                }
            }
        }

        // Update DNS servers
        if (resource.getDnsServers() != null) {
            // Clear existing DNS servers and add new ones
            for (String dnsServer : resource.getDnsServers()) {
                update = update.withDnsServer(dnsServer);
            }
        }

        // Update tags
        if (resource.getTags() != null) {
            update = update.withTags(resource.getTags());
        }

        vnet = update.apply();
        return mapNetworkToResource(vnet);
    }

    @Override
    public boolean delete(VnetResource resource) {
        if (resource.getId() == null && (resource.getName() == null || resource.getResourceGroup() == null)) {
            log.warn("Cannot delete VNet without id or (name and resourceGroup)");
            return false;
        }

        log.info("Deleting VNet '{}' in resource group '{}'",
                resource.getName(), resource.getResourceGroup());

        try {
            if (resource.getId() != null) {
                networkManager.networks().deleteById(resource.getId());
            } else {
                networkManager.networks()
                        .deleteByResourceGroup(resource.getResourceGroup(), resource.getName());
            }

            log.info("Deleted VNet: {}", resource.getName());
            return true;

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("ResourceNotFound")) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public List<Diagnostic> validate(VnetResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getName() == null || resource.getName().isBlank()) {
            diagnostics.add(Diagnostic.error("name is required")
                    .withProperty("name"));
        } else {
            // Azure VNet name validation
            if (resource.getName().length() < 2 || resource.getName().length() > 64) {
                diagnostics.add(Diagnostic.error("name must be between 2 and 64 characters")
                        .withProperty("name"));
            }
            if (!resource.getName().matches("^[a-zA-Z0-9][a-zA-Z0-9_.-]*[a-zA-Z0-9_]$") &&
                resource.getName().length() > 1) {
                diagnostics.add(Diagnostic.error("name must start with alphanumeric, " +
                        "can contain alphanumeric, underscores, periods, and hyphens, " +
                        "and must end with alphanumeric or underscore")
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

        if (resource.getAddressSpaces() == null || resource.getAddressSpaces().isEmpty()) {
            diagnostics.add(Diagnostic.error("at least one addressSpace is required")
                    .withProperty("addressSpaces"));
        } else {
            // Validate CIDR format for each address space
            for (int i = 0; i < resource.getAddressSpaces().size(); i++) {
                String cidr = resource.getAddressSpaces().get(i);
                if (!cidr.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}/\\d{1,2}$")) {
                    diagnostics.add(Diagnostic.error("Invalid CIDR format at index " + i,
                            "Expected format: x.x.x.x/x (e.g., 10.0.0.0/16)")
                            .withProperty("addressSpaces"));
                }
            }
        }

        return diagnostics;
    }

    private VnetResource mapNetworkToResource(Network vnet) {
        var resource = new VnetResource();

        // Input properties
        resource.setName(vnet.name());
        resource.setResourceGroup(vnet.resourceGroupName());
        resource.setLocation(vnet.regionName());
        resource.setAddressSpaces(new ArrayList<>(vnet.addressSpaces()));

        if (vnet.dnsServerIPs() != null && !vnet.dnsServerIPs().isEmpty()) {
            resource.setDnsServers(new ArrayList<>(vnet.dnsServerIPs()));
        }

        // These return primitive boolean
        resource.setEnableDdosProtection(vnet.isDdosProtectionEnabled());
        resource.setEnableVmProtection(vnet.isVmProtectionEnabled());

        // Tags
        if (vnet.tags() != null && !vnet.tags().isEmpty()) {
            resource.setTags(new HashMap<>(vnet.tags()));
        }

        // Cloud-managed properties
        resource.setId(vnet.id());
        resource.setResourceGuid(vnet.innerModel().resourceGuid());

        var provisioningState = vnet.innerModel().provisioningState();
        if (provisioningState != null) {
            resource.setProvisioningState(provisioningState.toString());
        }

        resource.setHasSubnets(vnet.subnets() != null && !vnet.subnets().isEmpty());

        return resource;
    }
}
