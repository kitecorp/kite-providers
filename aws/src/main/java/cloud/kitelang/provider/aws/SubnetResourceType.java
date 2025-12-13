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
 * ResourceTypeHandler for AWS Subnet.
 * Implements CRUD operations for Subnets using AWS EC2 SDK.
 */
@Log4j2
public class SubnetResourceType extends ResourceTypeHandler<SubnetResource> {

    private final Ec2Client ec2Client;

    public SubnetResourceType() {
        this.ec2Client = Ec2Client.create();
    }

    public SubnetResourceType(Ec2Client ec2Client) {
        this.ec2Client = ec2Client;
    }

    @Override
    public SubnetResource create(SubnetResource resource) {
        log.info("Creating Subnet in VPC '{}' with CIDR '{}'",
                resource.getVpcId(), resource.getCidrBlock());

        var requestBuilder = CreateSubnetRequest.builder()
                .vpcId(resource.getVpcId())
                .cidrBlock(resource.getCidrBlock());

        if (resource.getAvailabilityZone() != null) {
            requestBuilder.availabilityZone(resource.getAvailabilityZone());
        }

        if (resource.getIpv6CidrBlock() != null) {
            requestBuilder.ipv6CidrBlock(resource.getIpv6CidrBlock());
        }

        // Add tags during creation
        if (resource.getTags() != null && !resource.getTags().isEmpty()) {
            var tagSpecs = TagSpecification.builder()
                    .resourceType(ResourceType.SUBNET)
                    .tags(resource.getTags().entrySet().stream()
                            .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                            .collect(Collectors.toList()))
                    .build();
            requestBuilder.tagSpecifications(tagSpecs);
        }

        var response = ec2Client.createSubnet(requestBuilder.build());
        var subnet = response.subnet();
        log.info("Created Subnet: {}", subnet.subnetId());

        resource.setSubnetId(subnet.subnetId());

        // Configure subnet attributes
        if (Boolean.TRUE.equals(resource.getMapPublicIpOnLaunch())) {
            ec2Client.modifySubnetAttribute(ModifySubnetAttributeRequest.builder()
                    .subnetId(subnet.subnetId())
                    .mapPublicIpOnLaunch(AttributeBooleanValue.builder().value(true).build())
                    .build());
        }

        if (Boolean.TRUE.equals(resource.getAssignIpv6AddressOnCreation())) {
            ec2Client.modifySubnetAttribute(ModifySubnetAttributeRequest.builder()
                    .subnetId(subnet.subnetId())
                    .assignIpv6AddressOnCreation(AttributeBooleanValue.builder().value(true).build())
                    .build());
        }

        // Refresh to get all cloud-managed properties
        return read(resource);
    }

    @Override
    public SubnetResource read(SubnetResource resource) {
        if (resource.getSubnetId() == null) {
            log.warn("Cannot read Subnet without subnetId");
            return null;
        }

        log.info("Reading Subnet: {}", resource.getSubnetId());

        try {
            var response = ec2Client.describeSubnets(DescribeSubnetsRequest.builder()
                    .subnetIds(resource.getSubnetId())
                    .build());

            if (response.subnets().isEmpty()) {
                return null;
            }

            return mapToResource(response.subnets().get(0));

        } catch (Ec2Exception e) {
            if (e.awsErrorDetails().errorCode().equals("InvalidSubnetID.NotFound")) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public SubnetResource update(SubnetResource resource) {
        log.info("Updating Subnet: {}", resource.getSubnetId());

        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("Subnet not found: " + resource.getSubnetId());
        }

        String subnetId = resource.getSubnetId();

        // Update mapPublicIpOnLaunch if changed
        if (resource.getMapPublicIpOnLaunch() != null &&
                !resource.getMapPublicIpOnLaunch().equals(current.getMapPublicIpOnLaunch())) {
            ec2Client.modifySubnetAttribute(ModifySubnetAttributeRequest.builder()
                    .subnetId(subnetId)
                    .mapPublicIpOnLaunch(AttributeBooleanValue.builder()
                            .value(resource.getMapPublicIpOnLaunch())
                            .build())
                    .build());
        }

        // Update assignIpv6AddressOnCreation if changed
        if (resource.getAssignIpv6AddressOnCreation() != null &&
                !resource.getAssignIpv6AddressOnCreation().equals(current.getAssignIpv6AddressOnCreation())) {
            ec2Client.modifySubnetAttribute(ModifySubnetAttributeRequest.builder()
                    .subnetId(subnetId)
                    .assignIpv6AddressOnCreation(AttributeBooleanValue.builder()
                            .value(resource.getAssignIpv6AddressOnCreation())
                            .build())
                    .build());
        }

        // Update tags
        if (resource.getTags() != null) {
            // Delete existing tags
            if (current.getTags() != null && !current.getTags().isEmpty()) {
                var oldTags = current.getTags().entrySet().stream()
                        .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                        .collect(Collectors.toList());
                ec2Client.deleteTags(DeleteTagsRequest.builder()
                        .resources(subnetId)
                        .tags(oldTags)
                        .build());
            }
            // Apply new tags
            if (!resource.getTags().isEmpty()) {
                var newTags = resource.getTags().entrySet().stream()
                        .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                        .collect(Collectors.toList());
                ec2Client.createTags(CreateTagsRequest.builder()
                        .resources(subnetId)
                        .tags(newTags)
                        .build());
            }
        }

        return read(resource);
    }

    @Override
    public boolean delete(SubnetResource resource) {
        if (resource.getSubnetId() == null) {
            log.warn("Cannot delete Subnet without subnetId");
            return false;
        }

        log.info("Deleting Subnet: {}", resource.getSubnetId());

        try {
            ec2Client.deleteSubnet(DeleteSubnetRequest.builder()
                    .subnetId(resource.getSubnetId())
                    .build());

            log.info("Deleted Subnet: {}", resource.getSubnetId());
            return true;

        } catch (Ec2Exception e) {
            if (e.awsErrorDetails().errorCode().equals("InvalidSubnetID.NotFound")) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public List<Diagnostic> validate(SubnetResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getVpcId() == null || resource.getVpcId().isBlank()) {
            diagnostics.add(Diagnostic.error("vpcId is required")
                    .withProperty("vpcId"));
        }

        if (resource.getCidrBlock() == null || resource.getCidrBlock().isBlank()) {
            diagnostics.add(Diagnostic.error("cidrBlock is required")
                    .withProperty("cidrBlock"));
        } else {
            // Basic CIDR validation
            if (!resource.getCidrBlock().matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}/\\d{1,2}$")) {
                diagnostics.add(Diagnostic.error("cidrBlock must be a valid CIDR notation (e.g., 10.0.1.0/24)")
                        .withProperty("cidrBlock"));
            }
        }

        return diagnostics;
    }

    private SubnetResource mapToResource(Subnet subnet) {
        var resource = new SubnetResource();

        // Input properties
        resource.setVpcId(subnet.vpcId());
        resource.setCidrBlock(subnet.cidrBlock());
        resource.setAvailabilityZone(subnet.availabilityZone());
        resource.setMapPublicIpOnLaunch(subnet.mapPublicIpOnLaunch());
        resource.setAssignIpv6AddressOnCreation(subnet.assignIpv6AddressOnCreation());

        // IPv6 CIDR
        if (subnet.ipv6CidrBlockAssociationSet() != null && !subnet.ipv6CidrBlockAssociationSet().isEmpty()) {
            resource.setIpv6CidrBlock(subnet.ipv6CidrBlockAssociationSet().get(0).ipv6CidrBlock());
        }

        // Tags
        if (subnet.tags() != null && !subnet.tags().isEmpty()) {
            resource.setTags(subnet.tags().stream()
                    .collect(Collectors.toMap(Tag::key, Tag::value)));
        }

        // Cloud-managed properties
        resource.setSubnetId(subnet.subnetId());
        resource.setState(subnet.stateAsString());
        resource.setAvailabilityZoneId(subnet.availabilityZoneId());
        resource.setAvailableIpAddressCount(subnet.availableIpAddressCount());
        resource.setDefaultForAz(subnet.defaultForAz());
        resource.setOwnerId(subnet.ownerId());
        resource.setArn(subnet.subnetArn());

        return resource;
    }
}
