package cloud.kitelang.provider.aws;

import cloud.kitelang.provider.Diagnostic;
import cloud.kitelang.provider.ResourceTypeHandler;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ResourceTypeHandler for AWS Load Balancer (ALB/NLB).
 * Implements CRUD operations using AWS ELBv2 SDK.
 */
@Slf4j
public class LoadBalancerResourceType extends ResourceTypeHandler<LoadBalancerResource> {

    private static final Set<String> VALID_TYPES = Set.of("application", "network", "gateway");
    private static final Set<String> VALID_SCHEMES = Set.of("internet-facing", "internal");
    private static final Set<String> VALID_IP_TYPES = Set.of("ipv4", "dualstack");

    private final ElasticLoadBalancingV2Client elbClient;

    public LoadBalancerResourceType() {
        this.elbClient = ElasticLoadBalancingV2Client.create();
    }

    public LoadBalancerResourceType(ElasticLoadBalancingV2Client elbClient) {
        this.elbClient = elbClient;
    }

    @Override
    public LoadBalancerResource create(LoadBalancerResource resource) {
        log.info("Creating Load Balancer: {}", resource.getName());

        var requestBuilder = CreateLoadBalancerRequest.builder()
                .name(resource.getName())
                .subnets(resource.getSubnets());

        // Type (default to application)
        var type = resource.getType() != null ? resource.getType() : "application";
        requestBuilder.type(LoadBalancerTypeEnum.fromValue(type));

        // Scheme
        if (resource.getScheme() != null) {
            requestBuilder.scheme(LoadBalancerSchemeEnum.fromValue(resource.getScheme()));
        }

        // Security groups (ALB only)
        if (resource.getSecurityGroups() != null && !resource.getSecurityGroups().isEmpty()) {
            requestBuilder.securityGroups(resource.getSecurityGroups());
        }

        // IP address type
        if (resource.getIpAddressType() != null) {
            requestBuilder.ipAddressType(IpAddressType.fromValue(resource.getIpAddressType()));
        }

        // Tags
        if (resource.getTags() != null && !resource.getTags().isEmpty()) {
            var tags = resource.getTags().entrySet().stream()
                    .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                    .collect(Collectors.toList());
            requestBuilder.tags(tags);
        }

        var response = elbClient.createLoadBalancer(requestBuilder.build());
        var lb = response.loadBalancers().get(0);

        log.info("Created Load Balancer: {}", lb.loadBalancerArn());

        // Set cloud-managed properties
        resource.setArn(lb.loadBalancerArn());
        resource.setDnsName(lb.dnsName());
        resource.setCanonicalHostedZoneId(lb.canonicalHostedZoneId());
        resource.setVpcId(lb.vpcId());

        // Wait for LB to be active
        waitForLoadBalancerActive(lb.loadBalancerArn());

        // Apply attributes
        applyAttributes(lb.loadBalancerArn(), resource);

        return read(resource);
    }

    @Override
    public LoadBalancerResource read(LoadBalancerResource resource) {
        if (resource.getArn() == null && resource.getName() == null) {
            log.warn("Cannot read Load Balancer without arn or name");
            return null;
        }

        log.info("Reading Load Balancer: {}", resource.getArn() != null ? resource.getArn() : resource.getName());

        try {
            DescribeLoadBalancersResponse response;
            if (resource.getArn() != null) {
                response = elbClient.describeLoadBalancers(DescribeLoadBalancersRequest.builder()
                        .loadBalancerArns(resource.getArn())
                        .build());
            } else {
                response = elbClient.describeLoadBalancers(DescribeLoadBalancersRequest.builder()
                        .names(resource.getName())
                        .build());
            }

            if (response.loadBalancers().isEmpty()) {
                return null;
            }

            return mapLoadBalancerToResource(response.loadBalancers().get(0));

        } catch (LoadBalancerNotFoundException e) {
            return null;
        }
    }

    @Override
    public LoadBalancerResource update(LoadBalancerResource resource) {
        log.info("Updating Load Balancer: {}", resource.getArn());

        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("Load Balancer not found: " + resource.getName());
        }

        // Update security groups (ALB only)
        if (resource.getSecurityGroups() != null && "application".equals(current.getType())) {
            elbClient.setSecurityGroups(SetSecurityGroupsRequest.builder()
                    .loadBalancerArn(resource.getArn())
                    .securityGroups(resource.getSecurityGroups())
                    .build());
        }

        // Update subnets
        if (resource.getSubnets() != null) {
            elbClient.setSubnets(SetSubnetsRequest.builder()
                    .loadBalancerArn(resource.getArn())
                    .subnets(resource.getSubnets())
                    .build());
        }

        // Update IP address type
        if (resource.getIpAddressType() != null) {
            elbClient.setIpAddressType(SetIpAddressTypeRequest.builder()
                    .loadBalancerArn(resource.getArn())
                    .ipAddressType(IpAddressType.fromValue(resource.getIpAddressType()))
                    .build());
        }

        // Update attributes
        applyAttributes(resource.getArn(), resource);

        // Update tags
        if (resource.getTags() != null) {
            // Remove existing tags
            var existingTags = elbClient.describeTags(DescribeTagsRequest.builder()
                    .resourceArns(resource.getArn())
                    .build());
            if (!existingTags.tagDescriptions().isEmpty()) {
                var tagKeys = existingTags.tagDescriptions().get(0).tags().stream()
                        .map(Tag::key)
                        .collect(Collectors.toList());
                if (!tagKeys.isEmpty()) {
                    elbClient.removeTags(RemoveTagsRequest.builder()
                            .resourceArns(resource.getArn())
                            .tagKeys(tagKeys)
                            .build());
                }
            }

            // Add new tags
            if (!resource.getTags().isEmpty()) {
                var tags = resource.getTags().entrySet().stream()
                        .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                        .collect(Collectors.toList());
                elbClient.addTags(AddTagsRequest.builder()
                        .resourceArns(resource.getArn())
                        .tags(tags)
                        .build());
            }
        }

        return read(resource);
    }

    @Override
    public boolean delete(LoadBalancerResource resource) {
        if (resource.getArn() == null) {
            log.warn("Cannot delete Load Balancer without arn");
            return false;
        }

        log.info("Deleting Load Balancer: {}", resource.getArn());

        try {
            elbClient.deleteLoadBalancer(DeleteLoadBalancerRequest.builder()
                    .loadBalancerArn(resource.getArn())
                    .build());

            log.info("Deleted Load Balancer: {}", resource.getArn());
            return true;

        } catch (LoadBalancerNotFoundException e) {
            return false;
        }
    }

    @Override
    public List<Diagnostic> validate(LoadBalancerResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getName() == null || resource.getName().isBlank()) {
            diagnostics.add(Diagnostic.error("name is required")
                    .withProperty("name"));
        } else {
            if (resource.getName().length() > 32) {
                diagnostics.add(Diagnostic.error("name must be 32 characters or less")
                        .withProperty("name"));
            }
            if (!resource.getName().matches("^[a-zA-Z0-9][-a-zA-Z0-9]*$")) {
                diagnostics.add(Diagnostic.error("name must start with alphanumeric and contain only alphanumeric and hyphens")
                        .withProperty("name"));
            }
        }

        if (resource.getSubnets() == null || resource.getSubnets().isEmpty()) {
            diagnostics.add(Diagnostic.error("subnets is required")
                    .withProperty("subnets"));
        } else if (resource.getSubnets().size() < 2) {
            diagnostics.add(Diagnostic.error("at least 2 subnets in different Availability Zones are required")
                    .withProperty("subnets"));
        }

        var type = resource.getType() != null ? resource.getType() : "application";
        if (!VALID_TYPES.contains(type)) {
            diagnostics.add(Diagnostic.error("Invalid type",
                    "Valid values: " + String.join(", ", VALID_TYPES))
                    .withProperty("type"));
        }

        if (resource.getScheme() != null && !VALID_SCHEMES.contains(resource.getScheme())) {
            diagnostics.add(Diagnostic.error("Invalid scheme",
                    "Valid values: " + String.join(", ", VALID_SCHEMES))
                    .withProperty("scheme"));
        }

        if (resource.getIpAddressType() != null && !VALID_IP_TYPES.contains(resource.getIpAddressType())) {
            diagnostics.add(Diagnostic.error("Invalid ipAddressType",
                    "Valid values: " + String.join(", ", VALID_IP_TYPES))
                    .withProperty("ipAddressType"));
        }

        // ALB requires security groups
        if ("application".equals(type) &&
            (resource.getSecurityGroups() == null || resource.getSecurityGroups().isEmpty())) {
            diagnostics.add(Diagnostic.error("securityGroups is required for Application Load Balancer")
                    .withProperty("securityGroups"));
        }

        // Idle timeout only for ALB
        if (resource.getIdleTimeout() != null && !"application".equals(type)) {
            diagnostics.add(Diagnostic.error("idleTimeout is only valid for Application Load Balancer")
                    .withProperty("idleTimeout"));
        }

        return diagnostics;
    }

    private void waitForLoadBalancerActive(String arn) {
        log.debug("Waiting for Load Balancer {} to be active", arn);

        int maxAttempts = 60;
        int attempt = 0;

        while (attempt < maxAttempts) {
            var response = elbClient.describeLoadBalancers(DescribeLoadBalancersRequest.builder()
                    .loadBalancerArns(arn)
                    .build());

            if (!response.loadBalancers().isEmpty()) {
                var state = response.loadBalancers().get(0).state().code();
                if (state == LoadBalancerStateEnum.ACTIVE) {
                    log.debug("Load Balancer {} is now active", arn);
                    return;
                }
                if (state == LoadBalancerStateEnum.FAILED) {
                    throw new RuntimeException("Load Balancer creation failed: " + arn);
                }
            }

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for Load Balancer", e);
            }
            attempt++;
        }

        throw new RuntimeException("Timeout waiting for Load Balancer to become active: " + arn);
    }

    private void applyAttributes(String arn, LoadBalancerResource resource) {
        var attributes = new ArrayList<LoadBalancerAttribute>();

        if (resource.getDeletionProtection() != null) {
            attributes.add(LoadBalancerAttribute.builder()
                    .key("deletion_protection.enabled")
                    .value(resource.getDeletionProtection().toString())
                    .build());
        }

        if (resource.getCrossZoneLoadBalancing() != null) {
            attributes.add(LoadBalancerAttribute.builder()
                    .key("load_balancing.cross_zone.enabled")
                    .value(resource.getCrossZoneLoadBalancing().toString())
                    .build());
        }

        if (resource.getEnableHttp2() != null) {
            attributes.add(LoadBalancerAttribute.builder()
                    .key("routing.http2.enabled")
                    .value(resource.getEnableHttp2().toString())
                    .build());
        }

        if (resource.getIdleTimeout() != null) {
            attributes.add(LoadBalancerAttribute.builder()
                    .key("idle_timeout.timeout_seconds")
                    .value(resource.getIdleTimeout().toString())
                    .build());
        }

        if (resource.getAccessLogsEnabled() != null) {
            attributes.add(LoadBalancerAttribute.builder()
                    .key("access_logs.s3.enabled")
                    .value(resource.getAccessLogsEnabled().toString())
                    .build());

            if (resource.getAccessLogsEnabled() && resource.getAccessLogsBucket() != null) {
                attributes.add(LoadBalancerAttribute.builder()
                        .key("access_logs.s3.bucket")
                        .value(resource.getAccessLogsBucket())
                        .build());

                if (resource.getAccessLogsPrefix() != null) {
                    attributes.add(LoadBalancerAttribute.builder()
                            .key("access_logs.s3.prefix")
                            .value(resource.getAccessLogsPrefix())
                            .build());
                }
            }
        }

        if (!attributes.isEmpty()) {
            elbClient.modifyLoadBalancerAttributes(ModifyLoadBalancerAttributesRequest.builder()
                    .loadBalancerArn(arn)
                    .attributes(attributes)
                    .build());
        }
    }

    private LoadBalancerResource mapLoadBalancerToResource(LoadBalancer lb) {
        var resource = new LoadBalancerResource();

        // Input properties
        resource.setName(lb.loadBalancerName());
        resource.setType(lb.typeAsString());
        resource.setScheme(lb.schemeAsString());
        resource.setIpAddressType(lb.ipAddressTypeAsString());

        if (lb.securityGroups() != null) {
            resource.setSecurityGroups(new ArrayList<>(lb.securityGroups()));
        }

        // Extract subnet IDs from availability zones
        if (lb.availabilityZones() != null) {
            resource.setSubnets(lb.availabilityZones().stream()
                    .map(AvailabilityZone::subnetId)
                    .collect(Collectors.toList()));
            resource.setAvailabilityZones(lb.availabilityZones().stream()
                    .map(AvailabilityZone::zoneName)
                    .collect(Collectors.toList()));
        }

        // Get attributes
        var attributesResponse = elbClient.describeLoadBalancerAttributes(
                DescribeLoadBalancerAttributesRequest.builder()
                        .loadBalancerArn(lb.loadBalancerArn())
                        .build());

        for (var attr : attributesResponse.attributes()) {
            switch (attr.key()) {
                case "deletion_protection.enabled":
                    resource.setDeletionProtection(Boolean.parseBoolean(attr.value()));
                    break;
                case "load_balancing.cross_zone.enabled":
                    resource.setCrossZoneLoadBalancing(Boolean.parseBoolean(attr.value()));
                    break;
                case "routing.http2.enabled":
                    resource.setEnableHttp2(Boolean.parseBoolean(attr.value()));
                    break;
                case "idle_timeout.timeout_seconds":
                    resource.setIdleTimeout(Integer.parseInt(attr.value()));
                    break;
                case "access_logs.s3.enabled":
                    resource.setAccessLogsEnabled(Boolean.parseBoolean(attr.value()));
                    break;
                case "access_logs.s3.bucket":
                    resource.setAccessLogsBucket(attr.value());
                    break;
                case "access_logs.s3.prefix":
                    resource.setAccessLogsPrefix(attr.value());
                    break;
            }
        }

        // Get tags
        var tagsResponse = elbClient.describeTags(DescribeTagsRequest.builder()
                .resourceArns(lb.loadBalancerArn())
                .build());
        if (!tagsResponse.tagDescriptions().isEmpty()) {
            resource.setTags(tagsResponse.tagDescriptions().get(0).tags().stream()
                    .collect(Collectors.toMap(Tag::key, Tag::value)));
        }

        // Cloud-managed properties
        resource.setArn(lb.loadBalancerArn());
        resource.setDnsName(lb.dnsName());
        resource.setCanonicalHostedZoneId(lb.canonicalHostedZoneId());
        resource.setVpcId(lb.vpcId());
        resource.setState(lb.state().codeAsString());

        return resource;
    }
}
