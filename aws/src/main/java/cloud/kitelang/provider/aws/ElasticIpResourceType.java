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
 * ResourceTypeHandler for AWS Elastic IP.
 * Implements CRUD operations for Elastic IPs using AWS EC2 SDK.
 */
@Log4j2
public class ElasticIpResourceType extends ResourceTypeHandler<ElasticIpResource> {

    private final Ec2Client ec2Client;

    public ElasticIpResourceType() {
        this.ec2Client = Ec2Client.create();
    }

    public ElasticIpResourceType(Ec2Client ec2Client) {
        this.ec2Client = ec2Client;
    }

    @Override
    public ElasticIpResource create(ElasticIpResource resource) {
        log.info("Allocating Elastic IP");

        var requestBuilder = AllocateAddressRequest.builder();

        // Set domain (vpc is default)
        String domain = resource.getDomain();
        if (domain == null || domain.isBlank()) {
            domain = "vpc";
        }
        requestBuilder.domain(DomainType.fromValue(domain));

        // Optional: network border group
        if (resource.getNetworkBorderGroup() != null) {
            requestBuilder.networkBorderGroup(resource.getNetworkBorderGroup());
        }

        // Optional: BYOIP pool
        if (resource.getPublicIpv4Pool() != null) {
            requestBuilder.publicIpv4Pool(resource.getPublicIpv4Pool());
        }

        // Add tags during creation
        if (resource.getTags() != null && !resource.getTags().isEmpty()) {
            var tagSpecs = TagSpecification.builder()
                    .resourceType(ResourceType.ELASTIC_IP)
                    .tags(resource.getTags().entrySet().stream()
                            .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                            .collect(Collectors.toList()))
                    .build();
            requestBuilder.tagSpecifications(tagSpecs);
        }

        var response = ec2Client.allocateAddress(requestBuilder.build());
        log.info("Allocated Elastic IP: {} (allocation: {})",
                response.publicIp(), response.allocationId());

        resource.setAllocationId(response.allocationId());
        resource.setPublicIp(response.publicIp());

        return read(resource);
    }

    @Override
    public ElasticIpResource read(ElasticIpResource resource) {
        if (resource.getAllocationId() == null) {
            log.warn("Cannot read Elastic IP without allocationId");
            return null;
        }

        log.info("Reading Elastic IP: {}", resource.getAllocationId());

        try {
            var response = ec2Client.describeAddresses(DescribeAddressesRequest.builder()
                    .allocationIds(resource.getAllocationId())
                    .build());

            if (response.addresses().isEmpty()) {
                return null;
            }

            return mapToResource(response.addresses().get(0));

        } catch (Ec2Exception e) {
            if (e.awsErrorDetails().errorCode().equals("InvalidAllocationID.NotFound")) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public ElasticIpResource update(ElasticIpResource resource) {
        log.info("Updating Elastic IP: {}", resource.getAllocationId());

        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("Elastic IP not found: " + resource.getAllocationId());
        }

        String allocationId = resource.getAllocationId();

        // Only tags can be updated
        if (resource.getTags() != null) {
            // Delete old tags
            if (current.getTags() != null && !current.getTags().isEmpty()) {
                var oldTags = current.getTags().entrySet().stream()
                        .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                        .collect(Collectors.toList());
                ec2Client.deleteTags(DeleteTagsRequest.builder()
                        .resources(allocationId)
                        .tags(oldTags)
                        .build());
            }
            // Create new tags
            if (!resource.getTags().isEmpty()) {
                var newTags = resource.getTags().entrySet().stream()
                        .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                        .collect(Collectors.toList());
                ec2Client.createTags(CreateTagsRequest.builder()
                        .resources(allocationId)
                        .tags(newTags)
                        .build());
            }
        }

        return read(resource);
    }

    @Override
    public boolean delete(ElasticIpResource resource) {
        if (resource.getAllocationId() == null) {
            log.warn("Cannot release Elastic IP without allocationId");
            return false;
        }

        log.info("Releasing Elastic IP: {}", resource.getAllocationId());

        try {
            // If associated, disassociate first
            var current = read(resource);
            if (current != null && current.getAssociationId() != null) {
                log.info("Disassociating Elastic IP before release");
                ec2Client.disassociateAddress(DisassociateAddressRequest.builder()
                        .associationId(current.getAssociationId())
                        .build());
            }

            ec2Client.releaseAddress(ReleaseAddressRequest.builder()
                    .allocationId(resource.getAllocationId())
                    .build());

            log.info("Released Elastic IP: {}", resource.getAllocationId());
            return true;

        } catch (Ec2Exception e) {
            if (e.awsErrorDetails().errorCode().equals("InvalidAllocationID.NotFound")) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public List<Diagnostic> validate(ElasticIpResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        String domain = resource.getDomain();
        if (domain != null && !domain.isBlank()) {
            if (!domain.equalsIgnoreCase("vpc") && !domain.equalsIgnoreCase("standard")) {
                diagnostics.add(Diagnostic.error("domain must be 'vpc' or 'standard'")
                        .withProperty("domain"));
            }
        }

        return diagnostics;
    }

    private ElasticIpResource mapToResource(Address addr) {
        var resource = new ElasticIpResource();

        // Input properties
        if (addr.domain() != null) {
            resource.setDomain(addr.domain().toString());
        }
        resource.setNetworkBorderGroup(addr.networkBorderGroup());
        resource.setPublicIpv4Pool(addr.publicIpv4Pool());

        // Tags
        if (addr.tags() != null && !addr.tags().isEmpty()) {
            resource.setTags(addr.tags().stream()
                    .collect(Collectors.toMap(Tag::key, Tag::value)));
        }

        // Cloud-managed properties
        resource.setAllocationId(addr.allocationId());
        resource.setPublicIp(addr.publicIp());
        resource.setInstanceId(addr.instanceId());
        resource.setAssociationId(addr.associationId());
        resource.setNetworkInterfaceId(addr.networkInterfaceId());
        resource.setPrivateIpAddress(addr.privateIpAddress());

        return resource;
    }
}
