package cloud.kitelang.provider.azure.networking;

import cloud.kitelang.provider.Diagnostic;
import cloud.kitelang.provider.ResourceTypeHandler;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.Region;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.network.NetworkManager;
import com.azure.resourcemanager.network.models.RouteNextHopType;
import com.azure.resourcemanager.network.models.RouteTable;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ResourceTypeHandler for Azure Route Table.
 * Implements CRUD operations for Route Tables using Azure SDK.
 */
@Slf4j
public class RouteTableResourceType extends ResourceTypeHandler<RouteTableResource> {

    private final NetworkManager networkManager;

    public RouteTableResourceType() {
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

    public RouteTableResourceType(NetworkManager networkManager) {
        this.networkManager = networkManager;
    }

    @Override
    public RouteTableResource create(RouteTableResource resource) {
        log.info("Creating Route Table '{}' in resource group '{}'",
                resource.getName(), resource.getResourceGroup());

        var definition = networkManager.routeTables()
                .define(resource.getName())
                .withRegion(Region.fromName(resource.getLocation()))
                .withExistingResourceGroup(resource.getResourceGroup());

        // Add routes
        if (resource.getRoutes() != null && !resource.getRoutes().isEmpty()) {
            for (RouteEntry route : resource.getRoutes()) {
                var routeDef = definition.defineRoute(route.getName())
                        .withDestinationAddressPrefix(route.getAddressPrefix());

                RouteNextHopType hopType = RouteNextHopType.fromString(route.getNextHopType());

                if (hopType == RouteNextHopType.VIRTUAL_APPLIANCE) {
                    definition = routeDef.withNextHopToVirtualAppliance(route.getNextHopIpAddress()).attach();
                } else if (hopType == RouteNextHopType.INTERNET) {
                    definition = routeDef.withNextHop(RouteNextHopType.INTERNET).attach();
                } else if (hopType == RouteNextHopType.VIRTUAL_NETWORK_GATEWAY) {
                    definition = routeDef.withNextHop(RouteNextHopType.VIRTUAL_NETWORK_GATEWAY).attach();
                } else if (hopType == RouteNextHopType.VNET_LOCAL) {
                    definition = routeDef.withNextHop(RouteNextHopType.VNET_LOCAL).attach();
                } else if (hopType == RouteNextHopType.NONE) {
                    definition = routeDef.withNextHop(RouteNextHopType.NONE).attach();
                }
            }
        }

        // Disable BGP propagation if specified
        if (Boolean.TRUE.equals(resource.getDisableBgpRoutePropagation())) {
            definition = definition.withDisableBgpRoutePropagation();
        }

        // Add tags
        if (resource.getTags() != null && !resource.getTags().isEmpty()) {
            definition = definition.withTags(resource.getTags());
        }

        RouteTable routeTable = definition.create();
        log.info("Created Route Table: {}", routeTable.id());

        return mapToResource(routeTable);
    }

    @Override
    public RouteTableResource read(RouteTableResource resource) {
        if (resource.getName() == null || resource.getResourceGroup() == null) {
            log.warn("Cannot read Route Table without name and resourceGroup");
            return null;
        }

        log.info("Reading Route Table '{}' in resource group '{}'",
                resource.getName(), resource.getResourceGroup());

        try {
            RouteTable routeTable;
            if (resource.getId() != null) {
                routeTable = networkManager.routeTables().getById(resource.getId());
            } else {
                routeTable = networkManager.routeTables()
                        .getByResourceGroup(resource.getResourceGroup(), resource.getName());
            }

            if (routeTable == null) {
                return null;
            }

            return mapToResource(routeTable);

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("ResourceNotFound")) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public RouteTableResource update(RouteTableResource resource) {
        log.info("Updating Route Table '{}' in resource group '{}'",
                resource.getName(), resource.getResourceGroup());

        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("Route Table not found: " + resource.getName());
        }

        RouteTable routeTable = networkManager.routeTables()
                .getByResourceGroup(resource.getResourceGroup(), resource.getName());

        var update = routeTable.update();

        // Update routes - remove old ones, add new ones
        if (current.getRoutes() != null) {
            for (RouteEntry route : current.getRoutes()) {
                update = update.withoutRoute(route.getName());
            }
        }

        if (resource.getRoutes() != null) {
            for (RouteEntry route : resource.getRoutes()) {
                var routeDef = update.defineRoute(route.getName())
                        .withDestinationAddressPrefix(route.getAddressPrefix());

                RouteNextHopType hopType = RouteNextHopType.fromString(route.getNextHopType());

                if (hopType == RouteNextHopType.VIRTUAL_APPLIANCE) {
                    update = routeDef.withNextHopToVirtualAppliance(route.getNextHopIpAddress()).attach();
                } else if (hopType == RouteNextHopType.INTERNET) {
                    update = routeDef.withNextHop(RouteNextHopType.INTERNET).attach();
                } else if (hopType == RouteNextHopType.VIRTUAL_NETWORK_GATEWAY) {
                    update = routeDef.withNextHop(RouteNextHopType.VIRTUAL_NETWORK_GATEWAY).attach();
                } else if (hopType == RouteNextHopType.VNET_LOCAL) {
                    update = routeDef.withNextHop(RouteNextHopType.VNET_LOCAL).attach();
                } else if (hopType == RouteNextHopType.NONE) {
                    update = routeDef.withNextHop(RouteNextHopType.NONE).attach();
                }
            }
        }

        // Update tags
        if (resource.getTags() != null) {
            update = update.withTags(resource.getTags());
        }

        routeTable = update.apply();
        return mapToResource(routeTable);
    }

    @Override
    public boolean delete(RouteTableResource resource) {
        if (resource.getName() == null || resource.getResourceGroup() == null) {
            log.warn("Cannot delete Route Table without name and resourceGroup");
            return false;
        }

        log.info("Deleting Route Table '{}' from resource group '{}'",
                resource.getName(), resource.getResourceGroup());

        try {
            if (resource.getId() != null) {
                networkManager.routeTables().deleteById(resource.getId());
            } else {
                networkManager.routeTables()
                        .deleteByResourceGroup(resource.getResourceGroup(), resource.getName());
            }

            log.info("Deleted Route Table: {}", resource.getName());
            return true;

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("ResourceNotFound")) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public List<Diagnostic> validate(RouteTableResource resource) {
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

        // Validate routes
        if (resource.getRoutes() != null) {
            for (int i = 0; i < resource.getRoutes().size(); i++) {
                var route = resource.getRoutes().get(i);
                String prefix = "routes[" + i + "]";

                if (route.getName() == null || route.getName().isBlank()) {
                    diagnostics.add(Diagnostic.error("route name is required")
                            .withProperty(prefix + ".name"));
                }

                if (route.getAddressPrefix() == null || route.getAddressPrefix().isBlank()) {
                    diagnostics.add(Diagnostic.error("route addressPrefix is required")
                            .withProperty(prefix + ".addressPrefix"));
                }

                if (route.getNextHopType() == null || route.getNextHopType().isBlank()) {
                    diagnostics.add(Diagnostic.error("route nextHopType is required")
                            .withProperty(prefix + ".nextHopType"));
                } else {
                    // Validate nextHopType
                    String hopType = route.getNextHopType();
                    if (!hopType.equals("VirtualNetworkGateway") &&
                            !hopType.equals("VnetLocal") &&
                            !hopType.equals("Internet") &&
                            !hopType.equals("VirtualAppliance") &&
                            !hopType.equals("None")) {
                        diagnostics.add(Diagnostic.error("nextHopType must be one of: " +
                                "VirtualNetworkGateway, VnetLocal, Internet, VirtualAppliance, None")
                                .withProperty(prefix + ".nextHopType"));
                    }

                    // VirtualAppliance requires nextHopIpAddress
                    if (hopType.equals("VirtualAppliance") &&
                            (route.getNextHopIpAddress() == null || route.getNextHopIpAddress().isBlank())) {
                        diagnostics.add(Diagnostic.error("nextHopIpAddress is required when nextHopType is VirtualAppliance")
                                .withProperty(prefix + ".nextHopIpAddress"));
                    }
                }
            }
        }

        return diagnostics;
    }

    private RouteTableResource mapToResource(RouteTable rt) {
        var resource = new RouteTableResource();

        // Input properties
        resource.setName(rt.name());
        resource.setResourceGroup(rt.resourceGroupName());
        resource.setLocation(rt.regionName());
        resource.setDisableBgpRoutePropagation(rt.isBgpRoutePropagationDisabled());

        // Map routes
        if (rt.routes() != null && !rt.routes().isEmpty()) {
            List<RouteEntry> routes = new ArrayList<>();
            for (var r : rt.routes().values()) {
                routes.add(RouteEntry.builder()
                        .name(r.name())
                        .addressPrefix(r.destinationAddressPrefix())
                        .nextHopType(r.nextHopType().toString())
                        .nextHopIpAddress(r.nextHopIpAddress())
                        .build());
            }
            resource.setRoutes(routes);
        }

        // Tags
        if (rt.tags() != null && !rt.tags().isEmpty()) {
            resource.setTags(new HashMap<>(rt.tags()));
        }

        // Cloud-managed properties
        resource.setId(rt.id());

        var provisioningState = rt.innerModel().provisioningState();
        if (provisioningState != null) {
            resource.setProvisioningState(provisioningState.toString());
        }

        // Get associated subnet IDs
        if (rt.innerModel().subnets() != null && !rt.innerModel().subnets().isEmpty()) {
            var subnetIds = rt.innerModel().subnets().stream()
                    .map(s -> s.id())
                    .collect(Collectors.toList());
            resource.setAssociatedSubnetIds(subnetIds);
        }

        return resource;
    }
}
