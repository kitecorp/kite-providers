package cloud.kitelang.provider.aws.dns;

import cloud.kitelang.provider.Diagnostic;
import cloud.kitelang.provider.ResourceTypeHandler;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.route53.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ResourceTypeHandler for AWS Route53 Hosted Zone.
 * Implements CRUD operations using AWS Route53 SDK.
 */
@Slf4j
public class HostedZoneResourceType extends ResourceTypeHandler<HostedZoneResource> {

    private volatile Route53Client route53Client;

    public HostedZoneResourceType() {
        // Client created lazily to pick up configuration
    }

    public HostedZoneResourceType(Route53Client route53Client) {
        this.route53Client = route53Client;
    }

    /**
     * Get or create a Route53 client.
     * Creates the client lazily to allow provider configuration to be applied first.
     */
    private Route53Client getClient() {
        if (route53Client == null) {
            synchronized (this) {
                if (route53Client == null) {
                    log.debug("Creating Route53 client with current AWS configuration");
                    route53Client = Route53Client.create();
                }
            }
        }
        return route53Client;
    }

    @Override
    public HostedZoneResource create(HostedZoneResource resource) {
        log.info("Creating Hosted Zone: {}", resource.getName());

        var requestBuilder = CreateHostedZoneRequest.builder()
                .name(resource.getName())
                .callerReference(UUID.randomUUID().toString());

        // Comment
        if (resource.getComment() != null) {
            requestBuilder.hostedZoneConfig(HostedZoneConfig.builder()
                    .comment(resource.getComment())
                    .privateZone(resource.getPrivateZone() != null && resource.getPrivateZone())
                    .build());
        } else if (resource.getPrivateZone() != null && resource.getPrivateZone()) {
            requestBuilder.hostedZoneConfig(HostedZoneConfig.builder()
                    .privateZone(true)
                    .build());
        }

        // VPC for private zone
        if (resource.getPrivateZone() != null && resource.getPrivateZone()) {
            requestBuilder.vpc(VPC.builder()
                    .vpcId(resource.getVpcId())
                    .vpcRegion(VPCRegion.fromValue(resource.getVpcRegion()))
                    .build());
        }

        var response = getClient().createHostedZone(requestBuilder.build());
        var hostedZone = response.hostedZone();

        log.info("Created Hosted Zone: {}", hostedZone.id());

        // Extract zone ID (remove /hostedzone/ prefix)
        var zoneId = hostedZone.id().replace("/hostedzone/", "");
        resource.setHostedZoneId(zoneId);

        // Apply tags
        if (resource.getTags() != null && !resource.getTags().isEmpty()) {
            var tags = resource.getTags().entrySet().stream()
                    .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                    .collect(Collectors.toList());

            getClient().changeTagsForResource(ChangeTagsForResourceRequest.builder()
                    .resourceType(TagResourceType.HOSTEDZONE)
                    .resourceId(zoneId)
                    .addTags(tags)
                    .build());
        }

        return read(resource);
    }

    @Override
    public HostedZoneResource read(HostedZoneResource resource) {
        if (resource.getHostedZoneId() == null && resource.getName() == null) {
            log.warn("Cannot read Hosted Zone without hostedZoneId or name");
            return null;
        }

        log.info("Reading Hosted Zone: {}",
                resource.getHostedZoneId() != null ? resource.getHostedZoneId() : resource.getName());

        try {
            HostedZone hostedZone;
            String zoneId;

            if (resource.getHostedZoneId() != null) {
                zoneId = resource.getHostedZoneId();
                var response = getClient().getHostedZone(GetHostedZoneRequest.builder()
                        .id(zoneId)
                        .build());
                hostedZone = response.hostedZone();
            } else {
                // Find by name
                var listResponse = getClient().listHostedZonesByName(ListHostedZonesByNameRequest.builder()
                        .dnsName(resource.getName())
                        .maxItems("1")
                        .build());

                if (listResponse.hostedZones().isEmpty()) {
                    return null;
                }

                hostedZone = listResponse.hostedZones().get(0);
                // Verify exact match (listHostedZonesByName returns zones >= name)
                if (!hostedZone.name().equals(resource.getName()) &&
                    !hostedZone.name().equals(resource.getName() + ".")) {
                    return null;
                }
                zoneId = hostedZone.id().replace("/hostedzone/", "");
            }

            return mapHostedZoneToResource(hostedZone, zoneId);

        } catch (NoSuchHostedZoneException e) {
            return null;
        }
    }

    @Override
    public HostedZoneResource update(HostedZoneResource resource) {
        log.info("Updating Hosted Zone: {}", resource.getHostedZoneId());

        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("Hosted Zone not found: " + resource.getHostedZoneId());
        }

        // Update comment
        if (resource.getComment() != null && !resource.getComment().equals(current.getComment())) {
            getClient().updateHostedZoneComment(UpdateHostedZoneCommentRequest.builder()
                    .id(resource.getHostedZoneId())
                    .comment(resource.getComment())
                    .build());
        }

        // Update tags
        if (resource.getTags() != null) {
            // Get existing tags
            var existingTagsResponse = getClient().listTagsForResource(ListTagsForResourceRequest.builder()
                    .resourceType(TagResourceType.HOSTEDZONE)
                    .resourceId(resource.getHostedZoneId())
                    .build());

            var existingKeys = existingTagsResponse.resourceTagSet().tags().stream()
                    .map(Tag::key)
                    .collect(Collectors.toList());

            // Remove all existing tags
            if (!existingKeys.isEmpty()) {
                getClient().changeTagsForResource(ChangeTagsForResourceRequest.builder()
                        .resourceType(TagResourceType.HOSTEDZONE)
                        .resourceId(resource.getHostedZoneId())
                        .removeTagKeys(existingKeys)
                        .build());
            }

            // Add new tags
            if (!resource.getTags().isEmpty()) {
                var tags = resource.getTags().entrySet().stream()
                        .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                        .collect(Collectors.toList());

                getClient().changeTagsForResource(ChangeTagsForResourceRequest.builder()
                        .resourceType(TagResourceType.HOSTEDZONE)
                        .resourceId(resource.getHostedZoneId())
                        .addTags(tags)
                        .build());
            }
        }

        return read(resource);
    }

    @Override
    public boolean delete(HostedZoneResource resource) {
        if (resource.getHostedZoneId() == null) {
            log.warn("Cannot delete Hosted Zone without hostedZoneId");
            return false;
        }

        log.info("Deleting Hosted Zone: {}", resource.getHostedZoneId());

        try {
            getClient().deleteHostedZone(DeleteHostedZoneRequest.builder()
                    .id(resource.getHostedZoneId())
                    .build());

            log.info("Deleted Hosted Zone: {}", resource.getHostedZoneId());
            return true;

        } catch (NoSuchHostedZoneException e) {
            return false;
        } catch (HostedZoneNotEmptyException e) {
            throw new RuntimeException("Cannot delete hosted zone: it still contains resource record sets", e);
        }
    }

    @Override
    public List<Diagnostic> validate(HostedZoneResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getName() == null || resource.getName().isBlank()) {
            diagnostics.add(Diagnostic.error("name is required")
                    .withProperty("name"));
        } else {
            // Validate domain name format
            if (!resource.getName().matches("^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)*\\.?$")) {
                diagnostics.add(Diagnostic.error("Invalid domain name format")
                        .withProperty("name"));
            }
        }

        // Private zone validation
        if (resource.getPrivateZone() != null && resource.getPrivateZone()) {
            if (resource.getVpcId() == null || resource.getVpcId().isBlank()) {
                diagnostics.add(Diagnostic.error("vpcId is required for private hosted zones")
                        .withProperty("vpcId"));
            }
            if (resource.getVpcRegion() == null || resource.getVpcRegion().isBlank()) {
                diagnostics.add(Diagnostic.error("vpcRegion is required for private hosted zones")
                        .withProperty("vpcRegion"));
            }
        }

        return diagnostics;
    }

    private HostedZoneResource mapHostedZoneToResource(HostedZone hostedZone, String zoneId) {
        var resource = new HostedZoneResource();

        // Input properties
        resource.setName(hostedZone.name());
        if (hostedZone.config() != null) {
            resource.setComment(hostedZone.config().comment());
            resource.setPrivateZone(hostedZone.config().privateZone());
        }

        // Cloud-managed properties
        resource.setHostedZoneId(zoneId);
        resource.setResourceRecordSetCount(hostedZone.resourceRecordSetCount());
        resource.setCallerReference(hostedZone.callerReference());
        resource.setArn("arn:aws:route53:::hostedzone/" + zoneId);

        // Get name servers (for public zones)
        if (resource.getPrivateZone() == null || !resource.getPrivateZone()) {
            try {
                var zoneResponse = getClient().getHostedZone(GetHostedZoneRequest.builder()
                        .id(zoneId)
                        .build());
                if (zoneResponse.delegationSet() != null) {
                    resource.setNameServers(zoneResponse.delegationSet().nameServers());
                }
            } catch (Exception e) {
                log.warn("Could not retrieve name servers for zone {}", zoneId, e);
            }
        }

        // Get tags
        try {
            var tagsResponse = getClient().listTagsForResource(ListTagsForResourceRequest.builder()
                    .resourceType(TagResourceType.HOSTEDZONE)
                    .resourceId(zoneId)
                    .build());

            if (tagsResponse.resourceTagSet() != null && tagsResponse.resourceTagSet().tags() != null) {
                resource.setTags(tagsResponse.resourceTagSet().tags().stream()
                        .collect(Collectors.toMap(Tag::key, Tag::value)));
            }
        } catch (Exception e) {
            log.warn("Could not retrieve tags for zone {}", zoneId, e);
        }

        return resource;
    }
}
