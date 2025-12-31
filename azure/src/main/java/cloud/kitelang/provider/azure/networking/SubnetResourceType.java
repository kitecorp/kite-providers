package cloud.kitelang.provider.azure.networking;

import cloud.kitelang.provider.Diagnostic;
import cloud.kitelang.provider.ResourceTypeHandler;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.network.NetworkManager;
import com.azure.resourcemanager.network.models.Network;
import com.azure.resourcemanager.network.models.ServiceEndpointType;
import com.azure.resourcemanager.network.models.Subnet;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ResourceTypeHandler for Azure Subnet.
 * Implements CRUD operations for Subnets using Azure SDK.
 */
@Slf4j
public class SubnetResourceType extends ResourceTypeHandler<SubnetResource> {

    private final NetworkManager networkManager;

    public SubnetResourceType() {
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

    public SubnetResourceType(NetworkManager networkManager) {
        this.networkManager = networkManager;
    }

    @Override
    public SubnetResource create(SubnetResource resource) {
        log.info("Creating Subnet '{}' in VNet '{}' with address prefix '{}'",
                resource.getName(), resource.getVnetName(), resource.getAddressPrefix());

        // Get the parent VNet
        Network vnet = networkManager.networks()
                .getByResourceGroup(resource.getResourceGroup(), resource.getVnetName());

        if (vnet == null) {
            throw new RuntimeException("VNet not found: " + resource.getVnetName());
        }

        // Build subnet definition
        var subnetDef = vnet.update()
                .defineSubnet(resource.getName())
                .withAddressPrefix(resource.getAddressPrefix());

        // Associate NSG if specified
        if (resource.getNetworkSecurityGroupId() != null) {
            subnetDef = subnetDef.withExistingNetworkSecurityGroup(resource.getNetworkSecurityGroupId());
        }

        // Associate Route Table if specified
        if (resource.getRouteTableId() != null) {
            subnetDef = subnetDef.withExistingRouteTable(resource.getRouteTableId());
        }

        // Add service endpoints if specified
        if (resource.getServiceEndpoints() != null && !resource.getServiceEndpoints().isEmpty()) {
            for (String endpoint : resource.getServiceEndpoints()) {
                subnetDef = subnetDef.withAccessFromService(ServiceEndpointType.fromString(endpoint));
            }
        }

        // Create the subnet
        subnetDef.attach();
        vnet = vnet.update().apply();

        log.info("Created Subnet: {}", resource.getName());

        // Get the created subnet
        Subnet subnet = vnet.subnets().get(resource.getName());
        return mapSubnetToResource(subnet, resource.getResourceGroup(), resource.getVnetName());
    }

    @Override
    public SubnetResource read(SubnetResource resource) {
        if (resource.getName() == null || resource.getVnetName() == null || resource.getResourceGroup() == null) {
            log.warn("Cannot read Subnet without name, vnetName, and resourceGroup");
            return null;
        }

        log.info("Reading Subnet '{}' in VNet '{}'", resource.getName(), resource.getVnetName());

        try {
            Network vnet = networkManager.networks()
                    .getByResourceGroup(resource.getResourceGroup(), resource.getVnetName());

            if (vnet == null) {
                return null;
            }

            Subnet subnet = vnet.subnets().get(resource.getName());
            if (subnet == null) {
                return null;
            }

            return mapSubnetToResource(subnet, resource.getResourceGroup(), resource.getVnetName());

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("ResourceNotFound")) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public SubnetResource update(SubnetResource resource) {
        log.info("Updating Subnet '{}' in VNet '{}'", resource.getName(), resource.getVnetName());

        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("Subnet not found: " + resource.getName());
        }

        Network vnet = networkManager.networks()
                .getByResourceGroup(resource.getResourceGroup(), resource.getVnetName());

        var update = vnet.update()
                .updateSubnet(resource.getName());

        // Update address prefix (requires subnet to be empty)
        if (resource.getAddressPrefix() != null &&
                !resource.getAddressPrefix().equals(current.getAddressPrefix())) {
            update = update.withAddressPrefix(resource.getAddressPrefix());
        }

        // Update NSG association
        if (resource.getNetworkSecurityGroupId() != null) {
            update = update.withExistingNetworkSecurityGroup(resource.getNetworkSecurityGroupId());
        }

        // Update Route Table association
        if (resource.getRouteTableId() != null) {
            update = update.withExistingRouteTable(resource.getRouteTableId());
        }

        update.parent().apply();

        return read(resource);
    }

    @Override
    public boolean delete(SubnetResource resource) {
        if (resource.getName() == null || resource.getVnetName() == null || resource.getResourceGroup() == null) {
            log.warn("Cannot delete Subnet without name, vnetName, and resourceGroup");
            return false;
        }

        log.info("Deleting Subnet '{}' from VNet '{}'", resource.getName(), resource.getVnetName());

        try {
            Network vnet = networkManager.networks()
                    .getByResourceGroup(resource.getResourceGroup(), resource.getVnetName());

            if (vnet == null) {
                return false;
            }

            if (!vnet.subnets().containsKey(resource.getName())) {
                return false;
            }

            vnet.update()
                    .withoutSubnet(resource.getName())
                    .apply();

            log.info("Deleted Subnet: {}", resource.getName());
            return true;

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("ResourceNotFound")) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public List<Diagnostic> validate(SubnetResource resource) {
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

        if (resource.getVnetName() == null || resource.getVnetName().isBlank()) {
            diagnostics.add(Diagnostic.error("vnetName is required")
                    .withProperty("vnetName"));
        }

        if (resource.getAddressPrefix() == null || resource.getAddressPrefix().isBlank()) {
            diagnostics.add(Diagnostic.error("addressPrefix is required")
                    .withProperty("addressPrefix"));
        } else {
            if (!resource.getAddressPrefix().matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}/\\d{1,2}$")) {
                diagnostics.add(Diagnostic.error("addressPrefix must be a valid CIDR notation (e.g., 10.0.1.0/24)")
                        .withProperty("addressPrefix"));
            }
        }

        return diagnostics;
    }

    private SubnetResource mapSubnetToResource(Subnet subnet, String resourceGroup, String vnetName) {
        var resource = new SubnetResource();

        // Input properties
        resource.setName(subnet.name());
        resource.setResourceGroup(resourceGroup);
        resource.setVnetName(vnetName);
        resource.setAddressPrefix(subnet.addressPrefix());

        // NSG association
        if (subnet.networkSecurityGroupId() != null) {
            resource.setNetworkSecurityGroupId(subnet.networkSecurityGroupId());
        }

        // Route Table association
        if (subnet.routeTableId() != null) {
            resource.setRouteTableId(subnet.routeTableId());
        }

        // Service endpoints
        if (subnet.servicesWithAccess() != null && !subnet.servicesWithAccess().isEmpty()) {
            resource.setServiceEndpoints(subnet.servicesWithAccess().keySet().stream()
                    .map(ServiceEndpointType::toString)
                    .collect(Collectors.toList()));
        }

        // Cloud-managed properties
        resource.setId(subnet.innerModel().id());

        var provisioningState = subnet.innerModel().provisioningState();
        if (provisioningState != null) {
            resource.setProvisioningState(provisioningState.toString());
        }

        if (subnet.innerModel().purpose() != null) {
            resource.setPurpose(subnet.innerModel().purpose());
        }

        return resource;
    }
}
