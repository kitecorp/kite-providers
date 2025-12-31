package cloud.kitelang.provider.aws.loadbalancing;

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
 * ResourceTypeHandler for AWS Target Group.
 * Implements CRUD operations using AWS ELBv2 SDK.
 */
@Slf4j
public class TargetGroupResourceType extends ResourceTypeHandler<TargetGroupResource> {

    private static final Set<String> VALID_PROTOCOLS = Set.of(
            "HTTP", "HTTPS", "TCP", "TLS", "UDP", "TCP_UDP", "GENEVE"
    );
    private static final Set<String> VALID_TARGET_TYPES = Set.of(
            "instance", "ip", "lambda", "alb"
    );

    private final ElasticLoadBalancingV2Client elbClient;

    public TargetGroupResourceType() {
        this.elbClient = ElasticLoadBalancingV2Client.create();
    }

    public TargetGroupResourceType(ElasticLoadBalancingV2Client elbClient) {
        this.elbClient = elbClient;
    }

    @Override
    public TargetGroupResource create(TargetGroupResource resource) {
        log.info("Creating Target Group: {}", resource.getName());

        var requestBuilder = CreateTargetGroupRequest.builder()
                .name(resource.getName());

        // Port and protocol
        if (resource.getPort() != null) {
            requestBuilder.port(resource.getPort());
        }
        if (resource.getProtocol() != null) {
            requestBuilder.protocol(ProtocolEnum.fromValue(resource.getProtocol()));
        }
        if (resource.getProtocolVersion() != null) {
            requestBuilder.protocolVersion(resource.getProtocolVersion());
        }

        // VPC
        if (resource.getVpcId() != null) {
            requestBuilder.vpcId(resource.getVpcId());
        }

        // Target type
        if (resource.getTargetType() != null) {
            requestBuilder.targetType(TargetTypeEnum.fromValue(resource.getTargetType()));
        }

        // IP address type
        if (resource.getIpAddressType() != null) {
            requestBuilder.ipAddressType(TargetGroupIpAddressTypeEnum.fromValue(resource.getIpAddressType()));
        }

        // Health check
        if (resource.getHealthCheck() != null) {
            var hc = resource.getHealthCheck();
            if (hc.getProtocol() != null) {
                requestBuilder.healthCheckProtocol(ProtocolEnum.fromValue(hc.getProtocol()));
            }
            if (hc.getPort() != null) {
                requestBuilder.healthCheckPort(hc.getPort());
            }
            if (hc.getPath() != null) {
                requestBuilder.healthCheckPath(hc.getPath());
            }
            if (hc.getInterval() != null) {
                requestBuilder.healthCheckIntervalSeconds(hc.getInterval());
            }
            if (hc.getTimeout() != null) {
                requestBuilder.healthCheckTimeoutSeconds(hc.getTimeout());
            }
            if (hc.getHealthyThreshold() != null) {
                requestBuilder.healthyThresholdCount(hc.getHealthyThreshold());
            }
            if (hc.getUnhealthyThreshold() != null) {
                requestBuilder.unhealthyThresholdCount(hc.getUnhealthyThreshold());
            }
            if (hc.getMatcher() != null) {
                requestBuilder.matcher(Matcher.builder().httpCode(hc.getMatcher()).build());
            }
        }

        // Tags
        if (resource.getTags() != null && !resource.getTags().isEmpty()) {
            var tags = resource.getTags().entrySet().stream()
                    .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                    .collect(Collectors.toList());
            requestBuilder.tags(tags);
        }

        var response = elbClient.createTargetGroup(requestBuilder.build());
        var tg = response.targetGroups().get(0);

        log.info("Created Target Group: {}", tg.targetGroupArn());

        resource.setArn(tg.targetGroupArn());

        // Apply attributes
        applyAttributes(tg.targetGroupArn(), resource);

        // Register targets
        if (resource.getTargets() != null && !resource.getTargets().isEmpty()) {
            registerTargets(tg.targetGroupArn(), resource.getTargets(), resource.getPort());
        }

        return read(resource);
    }

    @Override
    public TargetGroupResource read(TargetGroupResource resource) {
        if (resource.getArn() == null && resource.getName() == null) {
            log.warn("Cannot read Target Group without arn or name");
            return null;
        }

        log.info("Reading Target Group: {}", resource.getArn() != null ? resource.getArn() : resource.getName());

        try {
            DescribeTargetGroupsResponse response;
            if (resource.getArn() != null) {
                response = elbClient.describeTargetGroups(DescribeTargetGroupsRequest.builder()
                        .targetGroupArns(resource.getArn())
                        .build());
            } else {
                response = elbClient.describeTargetGroups(DescribeTargetGroupsRequest.builder()
                        .names(resource.getName())
                        .build());
            }

            if (response.targetGroups().isEmpty()) {
                return null;
            }

            return mapTargetGroupToResource(response.targetGroups().get(0));

        } catch (TargetGroupNotFoundException e) {
            return null;
        }
    }

    @Override
    public TargetGroupResource update(TargetGroupResource resource) {
        log.info("Updating Target Group: {}", resource.getArn());

        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("Target Group not found: " + resource.getName());
        }

        // Update health check
        if (resource.getHealthCheck() != null) {
            var hc = resource.getHealthCheck();
            var modifyBuilder = ModifyTargetGroupRequest.builder()
                    .targetGroupArn(resource.getArn());

            if (hc.getProtocol() != null) {
                modifyBuilder.healthCheckProtocol(ProtocolEnum.fromValue(hc.getProtocol()));
            }
            if (hc.getPort() != null) {
                modifyBuilder.healthCheckPort(hc.getPort());
            }
            if (hc.getPath() != null) {
                modifyBuilder.healthCheckPath(hc.getPath());
            }
            if (hc.getInterval() != null) {
                modifyBuilder.healthCheckIntervalSeconds(hc.getInterval());
            }
            if (hc.getTimeout() != null) {
                modifyBuilder.healthCheckTimeoutSeconds(hc.getTimeout());
            }
            if (hc.getHealthyThreshold() != null) {
                modifyBuilder.healthyThresholdCount(hc.getHealthyThreshold());
            }
            if (hc.getUnhealthyThreshold() != null) {
                modifyBuilder.unhealthyThresholdCount(hc.getUnhealthyThreshold());
            }
            if (hc.getMatcher() != null) {
                modifyBuilder.matcher(Matcher.builder().httpCode(hc.getMatcher()).build());
            }

            elbClient.modifyTargetGroup(modifyBuilder.build());
        }

        // Update attributes
        applyAttributes(resource.getArn(), resource);

        // Update targets
        if (resource.getTargets() != null) {
            // Get current targets
            var healthResponse = elbClient.describeTargetHealth(DescribeTargetHealthRequest.builder()
                    .targetGroupArn(resource.getArn())
                    .build());
            var currentTargetIds = healthResponse.targetHealthDescriptions().stream()
                    .map(thd -> thd.target().id())
                    .collect(Collectors.toSet());

            // Deregister removed targets
            var toDeregister = currentTargetIds.stream()
                    .filter(id -> !resource.getTargets().contains(id))
                    .map(id -> TargetDescription.builder().id(id).build())
                    .collect(Collectors.toList());
            if (!toDeregister.isEmpty()) {
                elbClient.deregisterTargets(DeregisterTargetsRequest.builder()
                        .targetGroupArn(resource.getArn())
                        .targets(toDeregister)
                        .build());
            }

            // Register new targets
            var toRegister = resource.getTargets().stream()
                    .filter(id -> !currentTargetIds.contains(id))
                    .collect(Collectors.toList());
            if (!toRegister.isEmpty()) {
                registerTargets(resource.getArn(), toRegister, resource.getPort());
            }
        }

        // Update tags
        if (resource.getTags() != null) {
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
    public boolean delete(TargetGroupResource resource) {
        if (resource.getArn() == null) {
            log.warn("Cannot delete Target Group without arn");
            return false;
        }

        log.info("Deleting Target Group: {}", resource.getArn());

        try {
            elbClient.deleteTargetGroup(DeleteTargetGroupRequest.builder()
                    .targetGroupArn(resource.getArn())
                    .build());

            log.info("Deleted Target Group: {}", resource.getArn());
            return true;

        } catch (TargetGroupNotFoundException e) {
            return false;
        }
    }

    @Override
    public List<Diagnostic> validate(TargetGroupResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getName() == null || resource.getName().isBlank()) {
            diagnostics.add(Diagnostic.error("name is required")
                    .withProperty("name"));
        } else if (resource.getName().length() > 32) {
            diagnostics.add(Diagnostic.error("name must be 32 characters or less")
                    .withProperty("name"));
        }

        var targetType = resource.getTargetType() != null ? resource.getTargetType() : "instance";
        if (!VALID_TARGET_TYPES.contains(targetType)) {
            diagnostics.add(Diagnostic.error("Invalid targetType",
                    "Valid values: " + String.join(", ", VALID_TARGET_TYPES))
                    .withProperty("targetType"));
        }

        // Port and protocol required for instance/ip targets
        if ("instance".equals(targetType) || "ip".equals(targetType)) {
            if (resource.getPort() == null) {
                diagnostics.add(Diagnostic.error("port is required for instance/ip target types")
                        .withProperty("port"));
            }
            if (resource.getProtocol() == null) {
                diagnostics.add(Diagnostic.error("protocol is required for instance/ip target types")
                        .withProperty("protocol"));
            }
            if (resource.getVpcId() == null) {
                diagnostics.add(Diagnostic.error("vpcId is required for instance/ip target types")
                        .withProperty("vpcId"));
            }
        }

        if (resource.getProtocol() != null && !VALID_PROTOCOLS.contains(resource.getProtocol())) {
            diagnostics.add(Diagnostic.error("Invalid protocol",
                    "Valid values: " + String.join(", ", VALID_PROTOCOLS))
                    .withProperty("protocol"));
        }

        return diagnostics;
    }

    private void applyAttributes(String arn, TargetGroupResource resource) {
        var attributes = new ArrayList<TargetGroupAttribute>();

        if (resource.getDeregistrationDelay() != null) {
            attributes.add(TargetGroupAttribute.builder()
                    .key("deregistration_delay.timeout_seconds")
                    .value(resource.getDeregistrationDelay().toString())
                    .build());
        }

        if (resource.getSlowStart() != null) {
            attributes.add(TargetGroupAttribute.builder()
                    .key("slow_start.duration_seconds")
                    .value(resource.getSlowStart().toString())
                    .build());
        }

        if (resource.getStickiness() != null) {
            var stickiness = resource.getStickiness();
            if (stickiness.getEnabled() != null) {
                attributes.add(TargetGroupAttribute.builder()
                        .key("stickiness.enabled")
                        .value(stickiness.getEnabled().toString())
                        .build());
            }
            if (stickiness.getType() != null) {
                attributes.add(TargetGroupAttribute.builder()
                        .key("stickiness.type")
                        .value(stickiness.getType())
                        .build());
            }
            if (stickiness.getDuration() != null) {
                attributes.add(TargetGroupAttribute.builder()
                        .key("stickiness.lb_cookie.duration_seconds")
                        .value(stickiness.getDuration().toString())
                        .build());
            }
            if (stickiness.getCookieName() != null) {
                attributes.add(TargetGroupAttribute.builder()
                        .key("stickiness.app_cookie.cookie_name")
                        .value(stickiness.getCookieName())
                        .build());
            }
        }

        if (!attributes.isEmpty()) {
            elbClient.modifyTargetGroupAttributes(ModifyTargetGroupAttributesRequest.builder()
                    .targetGroupArn(arn)
                    .attributes(attributes)
                    .build());
        }
    }

    private void registerTargets(String arn, List<String> targets, Integer port) {
        var targetDescriptions = targets.stream()
                .map(id -> {
                    var builder = TargetDescription.builder().id(id);
                    if (port != null) {
                        builder.port(port);
                    }
                    return builder.build();
                })
                .collect(Collectors.toList());

        elbClient.registerTargets(RegisterTargetsRequest.builder()
                .targetGroupArn(arn)
                .targets(targetDescriptions)
                .build());
    }

    private TargetGroupResource mapTargetGroupToResource(TargetGroup tg) {
        var resource = new TargetGroupResource();

        // Input properties
        resource.setName(tg.targetGroupName());
        resource.setPort(tg.port());
        resource.setProtocol(tg.protocolAsString());
        resource.setProtocolVersion(tg.protocolVersion());
        resource.setVpcId(tg.vpcId());
        resource.setTargetType(tg.targetTypeAsString());
        resource.setIpAddressType(tg.ipAddressTypeAsString());

        // Health check
        var hc = TargetGroupResource.HealthCheck.builder()
                .protocol(tg.healthCheckProtocolAsString())
                .port(tg.healthCheckPort())
                .path(tg.healthCheckPath())
                .interval(tg.healthCheckIntervalSeconds())
                .timeout(tg.healthCheckTimeoutSeconds())
                .healthyThreshold(tg.healthyThresholdCount())
                .unhealthyThreshold(tg.unhealthyThresholdCount())
                .matcher(tg.matcher() != null ? tg.matcher().httpCode() : null)
                .build();
        resource.setHealthCheck(hc);

        // Get attributes
        var attributesResponse = elbClient.describeTargetGroupAttributes(
                DescribeTargetGroupAttributesRequest.builder()
                        .targetGroupArn(tg.targetGroupArn())
                        .build());

        TargetGroupResource.Stickiness stickiness = null;
        for (var attr : attributesResponse.attributes()) {
            switch (attr.key()) {
                case "deregistration_delay.timeout_seconds":
                    resource.setDeregistrationDelay(Integer.parseInt(attr.value()));
                    break;
                case "slow_start.duration_seconds":
                    resource.setSlowStart(Integer.parseInt(attr.value()));
                    break;
                case "stickiness.enabled":
                    if (stickiness == null) stickiness = new TargetGroupResource.Stickiness();
                    stickiness.setEnabled(Boolean.parseBoolean(attr.value()));
                    break;
                case "stickiness.type":
                    if (stickiness == null) stickiness = new TargetGroupResource.Stickiness();
                    stickiness.setType(attr.value());
                    break;
                case "stickiness.lb_cookie.duration_seconds":
                    if (stickiness == null) stickiness = new TargetGroupResource.Stickiness();
                    stickiness.setDuration(Integer.parseInt(attr.value()));
                    break;
            }
        }
        resource.setStickiness(stickiness);

        // Get registered targets
        var healthResponse = elbClient.describeTargetHealth(DescribeTargetHealthRequest.builder()
                .targetGroupArn(tg.targetGroupArn())
                .build());
        resource.setTargets(healthResponse.targetHealthDescriptions().stream()
                .map(thd -> thd.target().id())
                .collect(Collectors.toList()));

        // Get tags
        var tagsResponse = elbClient.describeTags(DescribeTagsRequest.builder()
                .resourceArns(tg.targetGroupArn())
                .build());
        if (!tagsResponse.tagDescriptions().isEmpty()) {
            resource.setTags(tagsResponse.tagDescriptions().get(0).tags().stream()
                    .collect(Collectors.toMap(Tag::key, Tag::value)));
        }

        // Cloud-managed properties
        resource.setArn(tg.targetGroupArn());
        resource.setLoadBalancerArns(tg.loadBalancerArns());

        return resource;
    }
}
