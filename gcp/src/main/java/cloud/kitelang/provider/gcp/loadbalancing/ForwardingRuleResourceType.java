package cloud.kitelang.provider.gcp.loadbalancing;

import cloud.kitelang.provider.Diagnostic;
import cloud.kitelang.provider.ResourceTypeHandler;
import cloud.kitelang.provider.gcp.GcpClientAware;
import com.google.cloud.compute.v1.*;
import com.google.cloud.dns.Dns;
import com.google.cloud.storage.Storage;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * ResourceTypeHandler for GCP Forwarding Rule.
 * Implements CRUD operations using the GCP Compute Java SDK.
 *
 * <p>Forwarding rules are regional resources in GCP and serve as the entry point
 * for load-balanced traffic.</p>
 */
@Slf4j
public class ForwardingRuleResourceType extends ResourceTypeHandler<ForwardingRuleResource> implements GcpClientAware {

    private static final Set<String> VALID_PROTOCOLS = Set.of(
            "TCP", "UDP", "ESP", "AH", "SCTP", "ICMP"
    );

    private static final Set<String> VALID_SCHEMES = Set.of(
            "EXTERNAL", "EXTERNAL_MANAGED", "INTERNAL", "INTERNAL_MANAGED", "INTERNAL_SELF_MANAGED"
    );

    private volatile ForwardingRulesClient forwardingRulesClient;
    private String project;
    private String defaultRegion;

    public ForwardingRuleResourceType() {
        // Client created lazily to pick up configuration
    }

    /** Constructor for testing with a mock client. */
    public ForwardingRuleResourceType(ForwardingRulesClient forwardingRulesClient, String project, String defaultRegion) {
        this.forwardingRulesClient = forwardingRulesClient;
        this.project = project;
        this.defaultRegion = defaultRegion;
    }

    @Override
    public void setGcpClients(InstancesClient instancesClient,
                              NetworksClient networksClient,
                              SubnetworksClient subnetworksClient,
                              ForwardingRulesClient forwardingRulesClient,
                              Storage storage,
                              Dns dns) {
        this.forwardingRulesClient = forwardingRulesClient;
        this.project = System.getProperty("gcp.project");
        this.defaultRegion = System.getProperty("gcp.region", "us-central1");
    }

    private ForwardingRulesClient getClient() {
        if (forwardingRulesClient == null) {
            synchronized (this) {
                if (forwardingRulesClient == null) {
                    try {
                        log.debug("Creating ForwardingRulesClient with default configuration");
                        forwardingRulesClient = ForwardingRulesClient.create();
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to create ForwardingRulesClient", e);
                    }
                }
            }
        }
        return forwardingRulesClient;
    }

    private String getProject() {
        if (project == null) {
            project = System.getProperty("gcp.project");
        }
        return project;
    }

    private String resolveRegion(ForwardingRuleResource resource) {
        if (resource.getRegion() != null) {
            return resource.getRegion();
        }
        return defaultRegion != null ? defaultRegion : "us-central1";
    }

    @Override
    public ForwardingRuleResource create(ForwardingRuleResource resource) {
        var region = resolveRegion(resource);
        log.info("Creating Forwarding Rule '{}' in region '{}'", resource.getName(), region);

        var ruleBuilder = ForwardingRule.newBuilder()
                .setName(resource.getName());

        if (resource.getTarget() != null) {
            ruleBuilder.setTarget(resource.getTarget());
        }
        if (resource.getPortRange() != null) {
            ruleBuilder.setPortRange(resource.getPortRange());
        }
        if (resource.getIpProtocol() != null) {
            ruleBuilder.setIPProtocol(resource.getIpProtocol());
        }
        if (resource.getLoadBalancingScheme() != null) {
            ruleBuilder.setLoadBalancingScheme(resource.getLoadBalancingScheme());
        }
        if (resource.getNetwork() != null) {
            ruleBuilder.setNetwork(resource.getNetwork());
        }
        if (resource.getSubnetwork() != null) {
            ruleBuilder.setSubnetwork(resource.getSubnetwork());
        }
        if (resource.getDescription() != null) {
            ruleBuilder.setDescription(resource.getDescription());
        }
        if (resource.getLabels() != null && !resource.getLabels().isEmpty()) {
            ruleBuilder.putAllLabels(resource.getLabels());
        }

        try {
            var operation = getClient().insertAsync(getProject(), region, ruleBuilder.build());
            operation.get();
            log.info("Created Forwarding Rule: {}", resource.getName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while creating forwarding rule", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to create forwarding rule: " + e.getCause().getMessage(), e.getCause());
        }

        return read(resource);
    }

    @Override
    public ForwardingRuleResource read(ForwardingRuleResource resource) {
        if (resource.getName() == null) {
            log.warn("Cannot read Forwarding Rule without name");
            return null;
        }

        var region = resolveRegion(resource);
        log.info("Reading Forwarding Rule: {} in region: {}", resource.getName(), region);

        try {
            var rule = getClient().get(getProject(), region, resource.getName());
            return mapToResource(rule, region);
        } catch (com.google.api.gax.rpc.NotFoundException e) {
            return null;
        }
    }

    @Override
    public ForwardingRuleResource update(ForwardingRuleResource resource) {
        var region = resolveRegion(resource);
        log.info("Updating Forwarding Rule: {} in region: {}", resource.getName(), region);

        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("Forwarding Rule not found: " + resource.getName());
        }

        // Forwarding rules are mostly immutable; labels can be updated
        if (resource.getLabels() != null) {
            try {
                var existingRule = getClient().get(getProject(), region, resource.getName());
                var labelsRequest = RegionSetLabelsRequest.newBuilder()
                        .setLabelFingerprint(existingRule.getLabelFingerprint())
                        .putAllLabels(resource.getLabels())
                        .build();
                var operation = getClient().setLabelsAsync(getProject(), region, resource.getName(), labelsRequest);
                operation.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while updating forwarding rule labels", e);
            } catch (ExecutionException e) {
                throw new RuntimeException("Failed to update forwarding rule labels: " + e.getCause().getMessage(), e.getCause());
            }
        }

        // Target can be updated via setTarget
        if (resource.getTarget() != null && !resource.getTarget().equals(current.getTarget())) {
            try {
                var targetRef = TargetReference.newBuilder()
                        .setTarget(resource.getTarget())
                        .build();
                var operation = getClient().setTargetAsync(getProject(), region, resource.getName(), targetRef);
                operation.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while updating forwarding rule target", e);
            } catch (ExecutionException e) {
                throw new RuntimeException("Failed to update forwarding rule target: " + e.getCause().getMessage(), e.getCause());
            }
        }

        return read(resource);
    }

    @Override
    public boolean delete(ForwardingRuleResource resource) {
        if (resource.getName() == null) {
            log.warn("Cannot delete Forwarding Rule without name");
            return false;
        }

        var region = resolveRegion(resource);
        log.info("Deleting Forwarding Rule: {} in region: {}", resource.getName(), region);

        try {
            var operation = getClient().deleteAsync(getProject(), region, resource.getName());
            operation.get();
            log.info("Deleted Forwarding Rule: {}", resource.getName());
            return true;
        } catch (com.google.api.gax.rpc.NotFoundException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while deleting forwarding rule", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof com.google.api.gax.rpc.NotFoundException) {
                return false;
            }
            throw new RuntimeException("Failed to delete forwarding rule: " + e.getCause().getMessage(), e.getCause());
        }
    }

    @Override
    public List<Diagnostic> validate(ForwardingRuleResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getName() == null || resource.getName().isBlank()) {
            diagnostics.add(Diagnostic.error("name is required").withProperty("name"));
        }

        if (resource.getIpProtocol() != null && !VALID_PROTOCOLS.contains(resource.getIpProtocol())) {
            diagnostics.add(Diagnostic.error("Invalid IP protocol",
                    "Valid values: " + String.join(", ", VALID_PROTOCOLS))
                    .withProperty("ipProtocol"));
        }

        if (resource.getLoadBalancingScheme() != null && !VALID_SCHEMES.contains(resource.getLoadBalancingScheme())) {
            diagnostics.add(Diagnostic.error("Invalid load balancing scheme",
                    "Valid values: " + String.join(", ", VALID_SCHEMES))
                    .withProperty("loadBalancingScheme"));
        }

        return diagnostics;
    }

    private ForwardingRuleResource mapToResource(ForwardingRule rule, String region) {
        var resource = new ForwardingRuleResource();

        resource.setName(rule.getName());
        resource.setTarget(rule.getTarget());
        resource.setPortRange(rule.getPortRange());
        resource.setIpProtocol(rule.getIPProtocol());
        resource.setLoadBalancingScheme(rule.getLoadBalancingScheme());
        resource.setNetwork(rule.getNetwork());
        resource.setSubnetwork(rule.getSubnetwork());
        resource.setRegion(region);
        resource.setDescription(rule.getDescription());

        if (rule.getLabelsMap() != null && !rule.getLabelsMap().isEmpty()) {
            resource.setLabels(new java.util.HashMap<>(rule.getLabelsMap()));
        }

        // Cloud-managed properties
        resource.setForwardingRuleId(String.valueOf(rule.getId()));
        resource.setSelfLink(rule.getSelfLink());
        resource.setIpAddress(rule.getIPAddress());

        return resource;
    }
}
