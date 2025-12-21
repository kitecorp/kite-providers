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
 * ResourceTypeHandler for AWS Load Balancer Listener.
 * Implements CRUD operations using AWS ELBv2 SDK.
 */
@Slf4j
public class ListenerResourceType extends ResourceTypeHandler<ListenerResource> {

    private static final Set<String> VALID_PROTOCOLS = Set.of(
            "HTTP", "HTTPS", "TCP", "TLS", "UDP", "TCP_UDP", "GENEVE"
    );
    private static final Set<String> SECURE_PROTOCOLS = Set.of("HTTPS", "TLS");

    private final ElasticLoadBalancingV2Client elbClient;

    public ListenerResourceType() {
        this.elbClient = ElasticLoadBalancingV2Client.create();
    }

    public ListenerResourceType(ElasticLoadBalancingV2Client elbClient) {
        this.elbClient = elbClient;
    }

    @Override
    public ListenerResource create(ListenerResource resource) {
        log.info("Creating Listener on port {} for LB: {}", resource.getPort(), resource.getLoadBalancerArn());

        var requestBuilder = CreateListenerRequest.builder()
                .loadBalancerArn(resource.getLoadBalancerArn())
                .port(resource.getPort())
                .protocol(ProtocolEnum.fromValue(resource.getProtocol()));

        // Certificate (for HTTPS/TLS)
        if (resource.getCertificateArn() != null) {
            requestBuilder.certificates(Certificate.builder()
                    .certificateArn(resource.getCertificateArn())
                    .build());
        }

        // SSL policy
        if (resource.getSslPolicy() != null) {
            requestBuilder.sslPolicy(resource.getSslPolicy());
        }

        // ALPN policy (NLB TLS)
        if (resource.getAlpnPolicy() != null) {
            requestBuilder.alpnPolicy(resource.getAlpnPolicy());
        }

        // Default action
        requestBuilder.defaultActions(buildDefaultAction(resource));

        // Tags
        if (resource.getTags() != null && !resource.getTags().isEmpty()) {
            var tags = resource.getTags().entrySet().stream()
                    .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                    .collect(Collectors.toList());
            requestBuilder.tags(tags);
        }

        var response = elbClient.createListener(requestBuilder.build());
        var listener = response.listeners().get(0);

        log.info("Created Listener: {}", listener.listenerArn());

        resource.setArn(listener.listenerArn());

        return read(resource);
    }

    @Override
    public ListenerResource read(ListenerResource resource) {
        if (resource.getArn() == null) {
            log.warn("Cannot read Listener without arn");
            return null;
        }

        log.info("Reading Listener: {}", resource.getArn());

        try {
            var response = elbClient.describeListeners(DescribeListenersRequest.builder()
                    .listenerArns(resource.getArn())
                    .build());

            if (response.listeners().isEmpty()) {
                return null;
            }

            return mapListenerToResource(response.listeners().get(0));

        } catch (ListenerNotFoundException e) {
            return null;
        }
    }

    @Override
    public ListenerResource update(ListenerResource resource) {
        log.info("Updating Listener: {}", resource.getArn());

        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("Listener not found: " + resource.getArn());
        }

        var modifyBuilder = ModifyListenerRequest.builder()
                .listenerArn(resource.getArn());

        // Port
        if (resource.getPort() != null) {
            modifyBuilder.port(resource.getPort());
        }

        // Protocol
        if (resource.getProtocol() != null) {
            modifyBuilder.protocol(ProtocolEnum.fromValue(resource.getProtocol()));
        }

        // Certificate
        if (resource.getCertificateArn() != null) {
            modifyBuilder.certificates(Certificate.builder()
                    .certificateArn(resource.getCertificateArn())
                    .build());
        }

        // SSL policy
        if (resource.getSslPolicy() != null) {
            modifyBuilder.sslPolicy(resource.getSslPolicy());
        }

        // ALPN policy
        if (resource.getAlpnPolicy() != null) {
            modifyBuilder.alpnPolicy(resource.getAlpnPolicy());
        }

        // Default action
        modifyBuilder.defaultActions(buildDefaultAction(resource));

        elbClient.modifyListener(modifyBuilder.build());

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
    public boolean delete(ListenerResource resource) {
        if (resource.getArn() == null) {
            log.warn("Cannot delete Listener without arn");
            return false;
        }

        log.info("Deleting Listener: {}", resource.getArn());

        try {
            elbClient.deleteListener(DeleteListenerRequest.builder()
                    .listenerArn(resource.getArn())
                    .build());

            log.info("Deleted Listener: {}", resource.getArn());
            return true;

        } catch (ListenerNotFoundException e) {
            return false;
        }
    }

    @Override
    public List<Diagnostic> validate(ListenerResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getLoadBalancerArn() == null || resource.getLoadBalancerArn().isBlank()) {
            diagnostics.add(Diagnostic.error("loadBalancerArn is required")
                    .withProperty("loadBalancerArn"));
        }

        if (resource.getPort() == null) {
            diagnostics.add(Diagnostic.error("port is required")
                    .withProperty("port"));
        } else if (resource.getPort() < 1 || resource.getPort() > 65535) {
            diagnostics.add(Diagnostic.error("port must be between 1 and 65535")
                    .withProperty("port"));
        }

        if (resource.getProtocol() == null || resource.getProtocol().isBlank()) {
            diagnostics.add(Diagnostic.error("protocol is required")
                    .withProperty("protocol"));
        } else if (!VALID_PROTOCOLS.contains(resource.getProtocol())) {
            diagnostics.add(Diagnostic.error("Invalid protocol",
                    "Valid values: " + String.join(", ", VALID_PROTOCOLS))
                    .withProperty("protocol"));
        }

        // Certificate required for HTTPS/TLS
        if (SECURE_PROTOCOLS.contains(resource.getProtocol()) && resource.getCertificateArn() == null) {
            diagnostics.add(Diagnostic.error("certificateArn is required for " + resource.getProtocol() + " listeners")
                    .withProperty("certificateArn"));
        }

        // Default action validation
        var actionType = resource.getDefaultActionType() != null ? resource.getDefaultActionType() : "forward";
        switch (actionType) {
            case "forward":
                if (resource.getDefaultTargetGroupArn() == null) {
                    diagnostics.add(Diagnostic.error("defaultTargetGroupArn is required for forward action")
                            .withProperty("defaultTargetGroupArn"));
                }
                break;
            case "redirect":
                if (resource.getRedirectConfig() == null) {
                    diagnostics.add(Diagnostic.error("redirectConfig is required for redirect action")
                            .withProperty("redirectConfig"));
                } else if (resource.getRedirectConfig().getStatusCode() == null) {
                    diagnostics.add(Diagnostic.error("redirectConfig.statusCode is required")
                            .withProperty("redirectConfig"));
                }
                break;
            case "fixed-response":
                if (resource.getFixedResponseConfig() == null) {
                    diagnostics.add(Diagnostic.error("fixedResponseConfig is required for fixed-response action")
                            .withProperty("fixedResponseConfig"));
                } else if (resource.getFixedResponseConfig().getStatusCode() == null) {
                    diagnostics.add(Diagnostic.error("fixedResponseConfig.statusCode is required")
                            .withProperty("fixedResponseConfig"));
                }
                break;
            default:
                diagnostics.add(Diagnostic.error("Invalid defaultActionType",
                        "Valid values: forward, redirect, fixed-response")
                        .withProperty("defaultActionType"));
        }

        return diagnostics;
    }

    private List<Action> buildDefaultAction(ListenerResource resource) {
        var actionType = resource.getDefaultActionType() != null ? resource.getDefaultActionType() : "forward";

        var actionBuilder = Action.builder()
                .type(ActionTypeEnum.fromValue(actionType.toUpperCase().replace("-", "_")));

        switch (actionType) {
            case "forward":
                actionBuilder.targetGroupArn(resource.getDefaultTargetGroupArn());
                break;
            case "redirect":
                var redirect = resource.getRedirectConfig();
                var redirectBuilder = RedirectActionConfig.builder()
                        .statusCode(RedirectActionStatusCodeEnum.fromValue(redirect.getStatusCode()));
                if (redirect.getProtocol() != null) redirectBuilder.protocol(redirect.getProtocol());
                if (redirect.getHost() != null) redirectBuilder.host(redirect.getHost());
                if (redirect.getPort() != null) redirectBuilder.port(redirect.getPort());
                if (redirect.getPath() != null) redirectBuilder.path(redirect.getPath());
                if (redirect.getQuery() != null) redirectBuilder.query(redirect.getQuery());
                actionBuilder.redirectConfig(redirectBuilder.build());
                break;
            case "fixed-response":
                var fixed = resource.getFixedResponseConfig();
                var fixedBuilder = FixedResponseActionConfig.builder()
                        .statusCode(fixed.getStatusCode());
                if (fixed.getContentType() != null) fixedBuilder.contentType(fixed.getContentType());
                if (fixed.getMessageBody() != null) fixedBuilder.messageBody(fixed.getMessageBody());
                actionBuilder.fixedResponseConfig(fixedBuilder.build());
                break;
        }

        return List.of(actionBuilder.build());
    }

    private ListenerResource mapListenerToResource(Listener listener) {
        var resource = new ListenerResource();

        // Input properties
        resource.setLoadBalancerArn(listener.loadBalancerArn());
        resource.setPort(listener.port());
        resource.setProtocol(listener.protocolAsString());
        resource.setSslPolicy(listener.sslPolicy());

        if (listener.alpnPolicy() != null && !listener.alpnPolicy().isEmpty()) {
            resource.setAlpnPolicy(listener.alpnPolicy().get(0));
        }

        if (listener.certificates() != null && !listener.certificates().isEmpty()) {
            resource.setCertificateArn(listener.certificates().get(0).certificateArn());
        }

        // Parse default action
        if (listener.defaultActions() != null && !listener.defaultActions().isEmpty()) {
            var action = listener.defaultActions().get(0);
            resource.setDefaultActionType(action.typeAsString().toLowerCase().replace("_", "-"));

            if (action.targetGroupArn() != null) {
                resource.setDefaultTargetGroupArn(action.targetGroupArn());
            }

            if (action.redirectConfig() != null) {
                var rc = action.redirectConfig();
                resource.setRedirectConfig(ListenerResource.RedirectConfig.builder()
                        .protocol(rc.protocol())
                        .host(rc.host())
                        .port(rc.port())
                        .path(rc.path())
                        .query(rc.query())
                        .statusCode(rc.statusCodeAsString())
                        .build());
            }

            if (action.fixedResponseConfig() != null) {
                var frc = action.fixedResponseConfig();
                resource.setFixedResponseConfig(ListenerResource.FixedResponseConfig.builder()
                        .statusCode(frc.statusCode())
                        .contentType(frc.contentType())
                        .messageBody(frc.messageBody())
                        .build());
            }
        }

        // Get tags
        var tagsResponse = elbClient.describeTags(DescribeTagsRequest.builder()
                .resourceArns(listener.listenerArn())
                .build());
        if (!tagsResponse.tagDescriptions().isEmpty()) {
            resource.setTags(tagsResponse.tagDescriptions().get(0).tags().stream()
                    .collect(Collectors.toMap(Tag::key, Tag::value)));
        }

        // Cloud-managed properties
        resource.setArn(listener.listenerArn());

        return resource;
    }
}
