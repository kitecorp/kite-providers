package cloud.kitelang.provider.aws;

import cloud.kitelang.provider.Diagnostic;
import cloud.kitelang.provider.ResourceTypeHandler;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ResourceTypeHandler for AWS Route Table.
 * Implements CRUD operations for Route Tables using AWS EC2 SDK.
 */
@Log4j2
public class RouteTableResourceType extends ResourceTypeHandler<RouteTableResource> {

    private final Ec2Client ec2Client;

    public RouteTableResourceType() {
        this.ec2Client = Ec2Client.create();
    }

    public RouteTableResourceType(Ec2Client ec2Client) {
        this.ec2Client = ec2Client;
    }

    @Override
    public RouteTableResource create(RouteTableResource resource) {
        log.info("Creating Route Table in VPC '{}'", resource.getVpcId());

        var requestBuilder = CreateRouteTableRequest.builder()
                .vpcId(resource.getVpcId());

        // Add tags during creation
        if (resource.getTags() != null && !resource.getTags().isEmpty()) {
            var tagSpecs = TagSpecification.builder()
                    .resourceType(ResourceType.ROUTE_TABLE)
                    .tags(resource.getTags().entrySet().stream()
                            .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                            .collect(Collectors.toList()))
                    .build();
            requestBuilder.tagSpecifications(tagSpecs);
        }

        var response = ec2Client.createRouteTable(requestBuilder.build());
        var routeTable = response.routeTable();
        log.info("Created Route Table: {}", routeTable.routeTableId());

        resource.setRouteTableId(routeTable.routeTableId());

        // Add routes
        if (resource.getRoutes() != null && !resource.getRoutes().isEmpty()) {
            for (Route route : resource.getRoutes()) {
                addRoute(routeTable.routeTableId(), route);
            }
        }

        return read(resource);
    }

    private void addRoute(String routeTableId, Route route) {
        var requestBuilder = CreateRouteRequest.builder()
                .routeTableId(routeTableId);

        if (route.getDestinationCidrBlock() != null) {
            requestBuilder.destinationCidrBlock(route.getDestinationCidrBlock());
        }
        if (route.getDestinationIpv6CidrBlock() != null) {
            requestBuilder.destinationIpv6CidrBlock(route.getDestinationIpv6CidrBlock());
        }
        if (route.getGatewayId() != null) {
            requestBuilder.gatewayId(route.getGatewayId());
        }
        if (route.getNatGatewayId() != null) {
            requestBuilder.natGatewayId(route.getNatGatewayId());
        }
        if (route.getNetworkInterfaceId() != null) {
            requestBuilder.networkInterfaceId(route.getNetworkInterfaceId());
        }
        if (route.getVpcPeeringConnectionId() != null) {
            requestBuilder.vpcPeeringConnectionId(route.getVpcPeeringConnectionId());
        }
        if (route.getTransitGatewayId() != null) {
            requestBuilder.transitGatewayId(route.getTransitGatewayId());
        }
        if (route.getVpcEndpointId() != null) {
            requestBuilder.vpcEndpointId(route.getVpcEndpointId());
        }
        if (route.getEgressOnlyInternetGatewayId() != null) {
            requestBuilder.egressOnlyInternetGatewayId(route.getEgressOnlyInternetGatewayId());
        }

        ec2Client.createRoute(requestBuilder.build());
        log.debug("Added route to {}: {}", routeTableId,
                route.getDestinationCidrBlock() != null ? route.getDestinationCidrBlock() : route.getDestinationIpv6CidrBlock());
    }

    @Override
    public RouteTableResource read(RouteTableResource resource) {
        if (resource.getRouteTableId() == null) {
            log.warn("Cannot read Route Table without routeTableId");
            return null;
        }

        log.info("Reading Route Table: {}", resource.getRouteTableId());

        try {
            var response = ec2Client.describeRouteTables(DescribeRouteTablesRequest.builder()
                    .routeTableIds(resource.getRouteTableId())
                    .build());

            if (response.routeTables().isEmpty()) {
                return null;
            }

            return mapToResource(response.routeTables().get(0));

        } catch (Ec2Exception e) {
            if (e.awsErrorDetails().errorCode().equals("InvalidRouteTableID.NotFound")) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public RouteTableResource update(RouteTableResource resource) {
        log.info("Updating Route Table: {}", resource.getRouteTableId());

        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("Route Table not found: " + resource.getRouteTableId());
        }

        String routeTableId = resource.getRouteTableId();

        // Update routes - delete old custom routes, add new ones
        // Skip the local route (VPC CIDR) which cannot be deleted
        if (current.getRoutes() != null) {
            for (Route route : current.getRoutes()) {
                // Skip local routes
                if (route.getGatewayId() != null && route.getGatewayId().equals("local")) {
                    continue;
                }
                deleteRoute(routeTableId, route);
            }
        }

        if (resource.getRoutes() != null) {
            for (Route route : resource.getRoutes()) {
                addRoute(routeTableId, route);
            }
        }

        // Update tags
        if (resource.getTags() != null) {
            if (current.getTags() != null && !current.getTags().isEmpty()) {
                var oldTags = current.getTags().entrySet().stream()
                        .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                        .collect(Collectors.toList());
                ec2Client.deleteTags(DeleteTagsRequest.builder()
                        .resources(routeTableId)
                        .tags(oldTags)
                        .build());
            }
            if (!resource.getTags().isEmpty()) {
                var newTags = resource.getTags().entrySet().stream()
                        .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                        .collect(Collectors.toList());
                ec2Client.createTags(CreateTagsRequest.builder()
                        .resources(routeTableId)
                        .tags(newTags)
                        .build());
            }
        }

        return read(resource);
    }

    private void deleteRoute(String routeTableId, Route route) {
        try {
            var requestBuilder = DeleteRouteRequest.builder()
                    .routeTableId(routeTableId);

            if (route.getDestinationCidrBlock() != null) {
                requestBuilder.destinationCidrBlock(route.getDestinationCidrBlock());
            } else if (route.getDestinationIpv6CidrBlock() != null) {
                requestBuilder.destinationIpv6CidrBlock(route.getDestinationIpv6CidrBlock());
            }

            ec2Client.deleteRoute(requestBuilder.build());
        } catch (Ec2Exception e) {
            log.debug("Could not delete route: {}", e.getMessage());
        }
    }

    @Override
    public boolean delete(RouteTableResource resource) {
        if (resource.getRouteTableId() == null) {
            log.warn("Cannot delete Route Table without routeTableId");
            return false;
        }

        log.info("Deleting Route Table: {}", resource.getRouteTableId());

        try {
            ec2Client.deleteRouteTable(DeleteRouteTableRequest.builder()
                    .routeTableId(resource.getRouteTableId())
                    .build());

            log.info("Deleted Route Table: {}", resource.getRouteTableId());
            return true;

        } catch (Ec2Exception e) {
            if (e.awsErrorDetails().errorCode().equals("InvalidRouteTableID.NotFound")) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public List<Diagnostic> validate(RouteTableResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getVpcId() == null || resource.getVpcId().isBlank()) {
            diagnostics.add(Diagnostic.error("vpcId is required")
                    .withProperty("vpcId"));
        }

        // Validate routes
        if (resource.getRoutes() != null) {
            for (int i = 0; i < resource.getRoutes().size(); i++) {
                var route = resource.getRoutes().get(i);
                String prefix = "routes[" + i + "]";

                // Must have a destination
                if (route.getDestinationCidrBlock() == null && route.getDestinationIpv6CidrBlock() == null) {
                    diagnostics.add(Diagnostic.error("route must have destinationCidrBlock or destinationIpv6CidrBlock")
                            .withProperty(prefix));
                }

                // Must have a target
                boolean hasTarget = route.getGatewayId() != null ||
                        route.getNatGatewayId() != null ||
                        route.getNetworkInterfaceId() != null ||
                        route.getVpcPeeringConnectionId() != null ||
                        route.getTransitGatewayId() != null ||
                        route.getVpcEndpointId() != null ||
                        route.getEgressOnlyInternetGatewayId() != null;

                if (!hasTarget) {
                    diagnostics.add(Diagnostic.error("route must have a target (gatewayId, natGatewayId, etc.)")
                            .withProperty(prefix));
                }
            }
        }

        return diagnostics;
    }

    private RouteTableResource mapToResource(software.amazon.awssdk.services.ec2.model.RouteTable rt) {
        var resource = new RouteTableResource();

        // Input properties
        resource.setVpcId(rt.vpcId());

        // Map routes (exclude local routes)
        if (rt.routes() != null && !rt.routes().isEmpty()) {
            var routes = rt.routes().stream()
                    .filter(r -> !"local".equals(r.gatewayId()))
                    .map(this::mapRoute)
                    .collect(Collectors.toList());
            if (!routes.isEmpty()) {
                resource.setRoutes(routes);
            }
        }

        // Tags
        if (rt.tags() != null && !rt.tags().isEmpty()) {
            resource.setTags(rt.tags().stream()
                    .collect(Collectors.toMap(Tag::key, Tag::value)));
        }

        // Cloud-managed properties
        resource.setRouteTableId(rt.routeTableId());
        resource.setOwnerId(rt.ownerId());

        // Check if main route table
        resource.setMain(rt.associations().stream()
                .anyMatch(RouteTableAssociation::main));

        // Get associated subnet IDs
        var subnetIds = rt.associations().stream()
                .filter(a -> a.subnetId() != null)
                .map(RouteTableAssociation::subnetId)
                .collect(Collectors.toList());
        if (!subnetIds.isEmpty()) {
            resource.setAssociatedSubnetIds(subnetIds);
        }

        return resource;
    }

    private Route mapRoute(software.amazon.awssdk.services.ec2.model.Route r) {
        return Route.builder()
                .destinationCidrBlock(r.destinationCidrBlock())
                .destinationIpv6CidrBlock(r.destinationIpv6CidrBlock())
                .gatewayId(r.gatewayId())
                .natGatewayId(r.natGatewayId())
                .networkInterfaceId(r.networkInterfaceId())
                .vpcPeeringConnectionId(r.vpcPeeringConnectionId())
                .transitGatewayId(r.transitGatewayId())
                .egressOnlyInternetGatewayId(r.egressOnlyInternetGatewayId())
                .build();
    }
}
