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
 * ResourceTypeHandler for AWS NAT Gateway.
 * Implements CRUD operations for NAT Gateways using AWS EC2 SDK.
 */
@Log4j2
public class NatGatewayResourceType extends ResourceTypeHandler<NatGatewayResource> {

    private final Ec2Client ec2Client;

    public NatGatewayResourceType() {
        this.ec2Client = Ec2Client.create();
    }

    public NatGatewayResourceType(Ec2Client ec2Client) {
        this.ec2Client = ec2Client;
    }

    @Override
    public NatGatewayResource create(NatGatewayResource resource) {
        log.info("Creating NAT Gateway in subnet '{}'", resource.getSubnetId());

        var requestBuilder = CreateNatGatewayRequest.builder()
                .subnetId(resource.getSubnetId());

        // Set connectivity type
        if (resource.getConnectivityType() != null) {
            requestBuilder.connectivityType(ConnectivityType.fromValue(resource.getConnectivityType().toUpperCase()));
        }

        // Allocation ID required for public NAT
        if (resource.getAllocationId() != null) {
            requestBuilder.allocationId(resource.getAllocationId());
        }

        // Add tags during creation
        if (resource.getTags() != null && !resource.getTags().isEmpty()) {
            var tagSpecs = TagSpecification.builder()
                    .resourceType(ResourceType.NATGATEWAY)
                    .tags(resource.getTags().entrySet().stream()
                            .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                            .collect(Collectors.toList()))
                    .build();
            requestBuilder.tagSpecifications(tagSpecs);
        }

        var response = ec2Client.createNatGateway(requestBuilder.build());
        var natGateway = response.natGateway();
        log.info("Created NAT Gateway: {} (state: {})",
                natGateway.natGatewayId(), natGateway.stateAsString());

        resource.setNatGatewayId(natGateway.natGatewayId());

        // Wait for NAT gateway to become available
        waitForNatGateway(natGateway.natGatewayId(), "available");

        return read(resource);
    }

    private void waitForNatGateway(String natGatewayId, String targetState) {
        log.info("Waiting for NAT Gateway '{}' to reach state '{}'", natGatewayId, targetState);

        int maxAttempts = 60;
        int attempt = 0;

        while (attempt < maxAttempts) {
            try {
                var response = ec2Client.describeNatGateways(DescribeNatGatewaysRequest.builder()
                        .natGatewayIds(natGatewayId)
                        .build());

                if (!response.natGateways().isEmpty()) {
                    String state = response.natGateways().get(0).stateAsString();
                    if (targetState.equals(state)) {
                        log.info("NAT Gateway '{}' is now '{}'", natGatewayId, state);
                        return;
                    }
                    if ("failed".equals(state)) {
                        throw new RuntimeException("NAT Gateway creation failed: " +
                                response.natGateways().get(0).failureMessage());
                    }
                }

                Thread.sleep(5000);
                attempt++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for NAT Gateway", e);
            }
        }

        log.warn("Timed out waiting for NAT Gateway '{}' to reach state '{}'", natGatewayId, targetState);
    }

    @Override
    public NatGatewayResource read(NatGatewayResource resource) {
        if (resource.getNatGatewayId() == null) {
            log.warn("Cannot read NAT Gateway without natGatewayId");
            return null;
        }

        log.info("Reading NAT Gateway: {}", resource.getNatGatewayId());

        try {
            var response = ec2Client.describeNatGateways(DescribeNatGatewaysRequest.builder()
                    .natGatewayIds(resource.getNatGatewayId())
                    .build());

            if (response.natGateways().isEmpty()) {
                return null;
            }

            var natGateway = response.natGateways().get(0);

            // Return null if deleted
            if ("deleted".equals(natGateway.stateAsString())) {
                return null;
            }

            return mapToResource(natGateway);

        } catch (Ec2Exception e) {
            if (e.awsErrorDetails().errorCode().equals("InvalidNatGatewayID.NotFound")) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public NatGatewayResource update(NatGatewayResource resource) {
        log.info("Updating NAT Gateway: {}", resource.getNatGatewayId());

        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("NAT Gateway not found: " + resource.getNatGatewayId());
        }

        String natGatewayId = resource.getNatGatewayId();

        // NAT Gateway doesn't support many updates - mainly tags
        if (resource.getTags() != null) {
            if (current.getTags() != null && !current.getTags().isEmpty()) {
                var oldTags = current.getTags().entrySet().stream()
                        .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                        .collect(Collectors.toList());
                ec2Client.deleteTags(DeleteTagsRequest.builder()
                        .resources(natGatewayId)
                        .tags(oldTags)
                        .build());
            }
            if (!resource.getTags().isEmpty()) {
                var newTags = resource.getTags().entrySet().stream()
                        .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                        .collect(Collectors.toList());
                ec2Client.createTags(CreateTagsRequest.builder()
                        .resources(natGatewayId)
                        .tags(newTags)
                        .build());
            }
        }

        return read(resource);
    }

    @Override
    public boolean delete(NatGatewayResource resource) {
        if (resource.getNatGatewayId() == null) {
            log.warn("Cannot delete NAT Gateway without natGatewayId");
            return false;
        }

        log.info("Deleting NAT Gateway: {}", resource.getNatGatewayId());

        try {
            ec2Client.deleteNatGateway(DeleteNatGatewayRequest.builder()
                    .natGatewayId(resource.getNatGatewayId())
                    .build());

            log.info("Deleted NAT Gateway: {}", resource.getNatGatewayId());
            return true;

        } catch (Ec2Exception e) {
            if (e.awsErrorDetails().errorCode().equals("InvalidNatGatewayID.NotFound")) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public List<Diagnostic> validate(NatGatewayResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getSubnetId() == null || resource.getSubnetId().isBlank()) {
            diagnostics.add(Diagnostic.error("subnetId is required")
                    .withProperty("subnetId"));
        }

        String connectivityType = resource.getConnectivityType();
        if (connectivityType == null || connectivityType.isBlank()) {
            connectivityType = "public";
        }

        if (!connectivityType.equalsIgnoreCase("public") &&
            !connectivityType.equalsIgnoreCase("private")) {
            diagnostics.add(Diagnostic.error("connectivityType must be 'public' or 'private'")
                    .withProperty("connectivityType"));
        }

        // Allocation ID required for public NAT gateway
        if (connectivityType.equalsIgnoreCase("public") &&
                (resource.getAllocationId() == null || resource.getAllocationId().isBlank())) {
            diagnostics.add(Diagnostic.error("allocationId is required for public NAT gateway")
                    .withProperty("allocationId"));
        }

        return diagnostics;
    }

    private NatGatewayResource mapToResource(NatGateway nat) {
        var resource = new NatGatewayResource();

        // Input properties
        resource.setSubnetId(nat.subnetId());
        resource.setConnectivityType(nat.connectivityTypeAsString());

        // Get addresses
        if (nat.natGatewayAddresses() != null && !nat.natGatewayAddresses().isEmpty()) {
            var addr = nat.natGatewayAddresses().get(0);
            resource.setAllocationId(addr.allocationId());
            resource.setPublicIp(addr.publicIp());
            resource.setPrivateIp(addr.privateIp());
        }

        // Tags
        if (nat.tags() != null && !nat.tags().isEmpty()) {
            resource.setTags(nat.tags().stream()
                    .collect(Collectors.toMap(Tag::key, Tag::value)));
        }

        // Cloud-managed properties
        resource.setNatGatewayId(nat.natGatewayId());
        resource.setState(nat.stateAsString());
        resource.setVpcId(nat.vpcId());

        return resource;
    }
}
