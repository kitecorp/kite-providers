package cloud.kitelang.provider.aws.networking;

import cloud.kitelang.provider.Diagnostic;
import cloud.kitelang.provider.ResourceTypeHandler;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ResourceTypeHandler for AWS Internet Gateway.
 * Implements CRUD operations for Internet Gateways using AWS EC2 SDK.
 */
@Slf4j
public class InternetGatewayResourceType extends ResourceTypeHandler<InternetGatewayResource> {

    private volatile Ec2Client ec2Client;

    public InternetGatewayResourceType() {
        // Client created lazily to pick up configuration
    }

    public InternetGatewayResourceType(Ec2Client ec2Client) {
        this.ec2Client = ec2Client;
    }

    /**
     * Get or create an EC2 client.
     * Creates the client lazily to allow provider configuration to be applied first.
     */
    private Ec2Client getClient() {
        if (ec2Client == null) {
            synchronized (this) {
                if (ec2Client == null) {
                    log.debug("Creating EC2 client with current AWS configuration");
                    ec2Client = Ec2Client.create();
                }
            }
        }
        return ec2Client;
    }

    @Override
    public InternetGatewayResource create(InternetGatewayResource resource) {
        log.info("Creating Internet Gateway");

        var requestBuilder = CreateInternetGatewayRequest.builder();

        // Add tags during creation
        if (resource.getTags() != null && !resource.getTags().isEmpty()) {
            var tagSpecs = TagSpecification.builder()
                    .resourceType(ResourceType.INTERNET_GATEWAY)
                    .tags(resource.getTags().entrySet().stream()
                            .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                            .collect(Collectors.toList()))
                    .build();
            requestBuilder.tagSpecifications(tagSpecs);
        }

        var response = getClient().createInternetGateway(requestBuilder.build());
        var igw = response.internetGateway();
        log.info("Created Internet Gateway: {}", igw.internetGatewayId());

        resource.setInternetGatewayId(igw.internetGatewayId());

        // Attach to VPC if specified
        if (resource.getVpcId() != null && !resource.getVpcId().isBlank()) {
            attachToVpc(igw.internetGatewayId(), resource.getVpcId());
        }

        return read(resource);
    }

    private void attachToVpc(String internetGatewayId, String vpcId) {
        log.info("Attaching Internet Gateway '{}' to VPC '{}'", internetGatewayId, vpcId);

        getClient().attachInternetGateway(AttachInternetGatewayRequest.builder()
                .internetGatewayId(internetGatewayId)
                .vpcId(vpcId)
                .build());
    }

    private void detachFromVpc(String internetGatewayId, String vpcId) {
        log.info("Detaching Internet Gateway '{}' from VPC '{}'", internetGatewayId, vpcId);

        try {
            getClient().detachInternetGateway(DetachInternetGatewayRequest.builder()
                    .internetGatewayId(internetGatewayId)
                    .vpcId(vpcId)
                    .build());
        } catch (Ec2Exception e) {
            log.debug("Could not detach internet gateway: {}", e.getMessage());
        }
    }

    @Override
    public InternetGatewayResource read(InternetGatewayResource resource) {
        if (resource.getInternetGatewayId() == null) {
            log.warn("Cannot read Internet Gateway without internetGatewayId");
            return null;
        }

        log.info("Reading Internet Gateway: {}", resource.getInternetGatewayId());

        try {
            var response = getClient().describeInternetGateways(DescribeInternetGatewaysRequest.builder()
                    .internetGatewayIds(resource.getInternetGatewayId())
                    .build());

            if (response.internetGateways().isEmpty()) {
                return null;
            }

            return mapToResource(response.internetGateways().get(0));

        } catch (Ec2Exception e) {
            if (e.awsErrorDetails().errorCode().equals("InvalidInternetGatewayID.NotFound")) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public InternetGatewayResource update(InternetGatewayResource resource) {
        log.info("Updating Internet Gateway: {}", resource.getInternetGatewayId());

        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("Internet Gateway not found: " + resource.getInternetGatewayId());
        }

        String igwId = resource.getInternetGatewayId();

        // Handle VPC attachment changes
        String currentVpcId = current.getVpcId();
        String desiredVpcId = resource.getVpcId();

        if (currentVpcId != null && !currentVpcId.equals(desiredVpcId)) {
            // Detach from current VPC
            detachFromVpc(igwId, currentVpcId);
        }

        if (desiredVpcId != null && !desiredVpcId.equals(currentVpcId)) {
            // Attach to new VPC
            attachToVpc(igwId, desiredVpcId);
        }

        // Update tags
        if (resource.getTags() != null) {
            if (current.getTags() != null && !current.getTags().isEmpty()) {
                var oldTags = current.getTags().entrySet().stream()
                        .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                        .collect(Collectors.toList());
                getClient().deleteTags(DeleteTagsRequest.builder()
                        .resources(igwId)
                        .tags(oldTags)
                        .build());
            }
            if (!resource.getTags().isEmpty()) {
                var newTags = resource.getTags().entrySet().stream()
                        .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                        .collect(Collectors.toList());
                getClient().createTags(CreateTagsRequest.builder()
                        .resources(igwId)
                        .tags(newTags)
                        .build());
            }
        }

        return read(resource);
    }

    @Override
    public boolean delete(InternetGatewayResource resource) {
        if (resource.getInternetGatewayId() == null) {
            log.warn("Cannot delete Internet Gateway without internetGatewayId");
            return false;
        }

        log.info("Deleting Internet Gateway: {}", resource.getInternetGatewayId());

        try {
            // First read to get current VPC attachment
            var current = read(resource);
            if (current == null) {
                return false;
            }

            // Detach from VPC if attached
            if (current.getVpcId() != null) {
                detachFromVpc(resource.getInternetGatewayId(), current.getVpcId());
            }

            // Delete the internet gateway
            getClient().deleteInternetGateway(DeleteInternetGatewayRequest.builder()
                    .internetGatewayId(resource.getInternetGatewayId())
                    .build());

            log.info("Deleted Internet Gateway: {}", resource.getInternetGatewayId());
            return true;

        } catch (Ec2Exception e) {
            if (e.awsErrorDetails().errorCode().equals("InvalidInternetGatewayID.NotFound")) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public List<Diagnostic> validate(InternetGatewayResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();
        // Internet Gateway has no required fields - VPC attachment is optional
        return diagnostics;
    }

    private InternetGatewayResource mapToResource(InternetGateway igw) {
        var resource = new InternetGatewayResource();

        // Check for VPC attachment
        if (igw.attachments() != null && !igw.attachments().isEmpty()) {
            var attachment = igw.attachments().get(0);
            resource.setVpcId(attachment.vpcId());
            resource.setAttachmentState(attachment.stateAsString());
        }

        // Tags
        if (igw.tags() != null && !igw.tags().isEmpty()) {
            resource.setTags(igw.tags().stream()
                    .collect(Collectors.toMap(Tag::key, Tag::value)));
        }

        // Cloud-managed properties
        resource.setInternetGatewayId(igw.internetGatewayId());
        resource.setOwnerId(igw.ownerId());

        return resource;
    }
}
