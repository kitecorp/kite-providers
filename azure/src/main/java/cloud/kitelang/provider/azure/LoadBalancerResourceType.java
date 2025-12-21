package cloud.kitelang.provider.azure;

import cloud.kitelang.provider.Diagnostic;
import cloud.kitelang.provider.ResourceTypeHandler;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.network.NetworkManager;
import com.azure.resourcemanager.network.models.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ResourceTypeHandler for Azure Load Balancer.
 * Implements CRUD operations using Azure Network SDK.
 */
@Slf4j
public class LoadBalancerResourceType extends ResourceTypeHandler<LoadBalancerResource> {

    private static final Set<String> VALID_SKUS = Set.of("Basic", "Standard");
    private static final Set<String> VALID_TIERS = Set.of("Regional", "Global");
    private static final Set<String> VALID_PROBE_PROTOCOLS = Set.of("Http", "Https", "Tcp");
    private static final Set<String> VALID_RULE_PROTOCOLS = Set.of("Tcp", "Udp", "All");

    private final NetworkManager networkManager;

    public LoadBalancerResourceType() {
        var credential = new DefaultAzureCredentialBuilder().build();

        String subscriptionId = System.getenv("AZURE_SUBSCRIPTION_ID");
        if (subscriptionId == null || subscriptionId.isBlank()) {
            throw new IllegalStateException(
                    "AZURE_SUBSCRIPTION_ID environment variable must be set");
        }

        String tenantId = System.getenv("AZURE_TENANT_ID");
        var profile = new AzureProfile(tenantId, subscriptionId, AzureEnvironment.AZURE);

        this.networkManager = NetworkManager.authenticate(credential, profile);
    }

    public LoadBalancerResourceType(NetworkManager networkManager) {
        this.networkManager = networkManager;
    }

    @Override
    public LoadBalancerResource create(LoadBalancerResource resource) {
        log.info("Creating Load Balancer: {} in {}", resource.getName(), resource.getResourceGroup());

        // Determine SKU
        var skuName = resource.getSku() != null ? resource.getSku() : "Standard";
        var skuType = "basic".equalsIgnoreCase(skuName) ? LoadBalancerSkuType.BASIC : LoadBalancerSkuType.STANDARD;

        if (resource.getFrontendIpConfigurations() == null || resource.getFrontendIpConfigurations().isEmpty()) {
            throw new RuntimeException("At least one frontend IP configuration is required");
        }

        var firstFrontend = resource.getFrontendIpConfigurations().get(0);

        // Azure Load Balancer fluent API requires creating through a load balancing rule
        // which establishes frontend, backend pool, and the rule in one chain
        var backendName = resource.getBackendAddressPools() != null && !resource.getBackendAddressPools().isEmpty()
                ? resource.getBackendAddressPools().get(0).getName()
                : "default-backend";

        // Start with a temporary rule to bootstrap the load balancer
        var definition = networkManager.loadBalancers()
                .define(resource.getName())
                .withRegion(resource.getLocation())
                .withExistingResourceGroup(resource.getResourceGroup());

        LoadBalancer.DefinitionStages.WithLBRuleOrNatOrCreate ruleStage;

        if (firstFrontend.getPublicIpAddressId() != null) {
            // Public load balancer
            var publicIp = networkManager.publicIpAddresses().getById(firstFrontend.getPublicIpAddressId());
            ruleStage = definition
                    .defineLoadBalancingRule("temp-bootstrap-rule")
                    .withProtocol(TransportProtocol.TCP)
                    .fromExistingPublicIPAddress(publicIp)
                    .fromFrontendPort(65535)
                    .toBackend(backendName)
                    .toBackendPort(65535)
                    .attach();
        } else if (firstFrontend.getSubnetId() != null) {
            // Internal load balancer
            var vnetId = firstFrontend.getSubnetId().substring(0, firstFrontend.getSubnetId().lastIndexOf("/subnets/"));
            var subnetName = firstFrontend.getSubnetId().substring(
                    firstFrontend.getSubnetId().lastIndexOf("/subnets/") + "/subnets/".length());
            var vnet = networkManager.networks().getById(vnetId);

            ruleStage = definition
                    .defineLoadBalancingRule("temp-bootstrap-rule")
                    .withProtocol(TransportProtocol.TCP)
                    .fromExistingSubnet(vnet, subnetName)
                    .fromFrontendPort(65535)
                    .toBackend(backendName)
                    .toBackendPort(65535)
                    .attach();
        } else {
            throw new RuntimeException("Either publicIpAddressId or subnetId is required for frontend");
        }

        // Set SKU and tags
        var createStage = ruleStage.withSku(skuType);
        if (resource.getTags() != null) {
            createStage = createStage.withTags(resource.getTags());
        }

        // Create the load balancer
        var lb = createStage.create();

        log.info("Created Load Balancer: {}", lb.id());

        // Remove temp rule and add full configuration
        lb = lb.update().withoutLoadBalancingRule("temp-bootstrap-rule").apply();

        // Update with full configuration (backends, probes, rules)
        resource.setId(lb.id());
        return updateConfiguration(resource, lb);
    }

    @Override
    public LoadBalancerResource read(LoadBalancerResource resource) {
        if (resource.getId() == null && (resource.getName() == null || resource.getResourceGroup() == null)) {
            log.warn("Cannot read Load Balancer without id or name/resourceGroup");
            return null;
        }

        log.info("Reading Load Balancer: {}", resource.getId() != null ? resource.getId() : resource.getName());

        try {
            LoadBalancer lb;
            if (resource.getId() != null) {
                lb = networkManager.loadBalancers().getById(resource.getId());
            } else {
                lb = networkManager.loadBalancers().getByResourceGroup(resource.getResourceGroup(), resource.getName());
            }

            if (lb == null) {
                return null;
            }

            return mapLoadBalancerToResource(lb);

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("ResourceNotFound")) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public LoadBalancerResource update(LoadBalancerResource resource) {
        log.info("Updating Load Balancer: {}", resource.getId());

        var lb = networkManager.loadBalancers().getById(resource.getId());
        if (lb == null) {
            throw new RuntimeException("Load Balancer not found: " + resource.getName());
        }

        return updateConfiguration(resource, lb);
    }

    private LoadBalancerResource updateConfiguration(LoadBalancerResource resource, LoadBalancer lb) {
        var update = lb.update();

        // Update tags
        if (resource.getTags() != null) {
            update.withTags(resource.getTags());
        }

        // Backend address pools
        if (resource.getBackendAddressPools() != null) {
            for (var pool : resource.getBackendAddressPools()) {
                if (!lb.backends().containsKey(pool.getName())) {
                    update.defineBackend(pool.getName()).attach();
                }
            }
        }

        // Health probes
        if (resource.getHealthProbes() != null) {
            for (var probe : resource.getHealthProbes()) {
                if (!lb.httpProbes().containsKey(probe.getName()) && !lb.tcpProbes().containsKey(probe.getName())) {
                    if ("Tcp".equalsIgnoreCase(probe.getProtocol())) {
                        var probeUpdate = update.defineTcpProbe(probe.getName())
                                .withPort(probe.getPort());
                        if (probe.getIntervalInSeconds() != null) {
                            probeUpdate.withIntervalInSeconds(probe.getIntervalInSeconds());
                        }
                        if (probe.getNumberOfProbes() != null) {
                            probeUpdate.withNumberOfProbes(probe.getNumberOfProbes());
                        }
                        probeUpdate.attach();
                    } else {
                        var probeUpdate = update.defineHttpProbe(probe.getName())
                                .withRequestPath(probe.getRequestPath() != null ? probe.getRequestPath() : "/");
                        if (probe.getPort() != null) {
                            probeUpdate.withPort(probe.getPort());
                        }
                        if (probe.getIntervalInSeconds() != null) {
                            probeUpdate.withIntervalInSeconds(probe.getIntervalInSeconds());
                        }
                        if (probe.getNumberOfProbes() != null) {
                            probeUpdate.withNumberOfProbes(probe.getNumberOfProbes());
                        }
                        probeUpdate.attach();
                    }
                }
            }
        }

        // Load balancing rules
        if (resource.getLoadBalancingRules() != null) {
            for (var rule : resource.getLoadBalancingRules()) {
                if (!lb.loadBalancingRules().containsKey(rule.getName())) {
                    var protocol = "Tcp".equalsIgnoreCase(rule.getProtocol()) ? TransportProtocol.TCP :
                                   "Udp".equalsIgnoreCase(rule.getProtocol()) ? TransportProtocol.UDP :
                                   TransportProtocol.ALL;

                    var ruleUpdate = update.defineLoadBalancingRule(rule.getName())
                            .withProtocol(protocol)
                            .fromFrontend(rule.getFrontendIpConfigurationName())
                            .fromFrontendPort(rule.getFrontendPort())
                            .toBackend(rule.getBackendAddressPoolName())
                            .toBackendPort(rule.getBackendPort());

                    if (rule.getProbeName() != null) {
                        ruleUpdate.withProbe(rule.getProbeName());
                    }
                    if (rule.getIdleTimeoutInMinutes() != null) {
                        ruleUpdate.withIdleTimeoutInMinutes(rule.getIdleTimeoutInMinutes());
                    }
                    if (rule.getEnableFloatingIP() != null && rule.getEnableFloatingIP()) {
                        ruleUpdate.withFloatingIP(true);
                    }
                    if (rule.getLoadDistribution() != null) {
                        var dist = switch (rule.getLoadDistribution().toLowerCase()) {
                            case "sourceip" -> LoadDistribution.SOURCE_IP;
                            default -> LoadDistribution.DEFAULT;
                        };
                        ruleUpdate.withLoadDistribution(dist);
                    }
                    ruleUpdate.attach();
                }
            }
        }

        // Inbound NAT rules
        if (resource.getInboundNatRules() != null) {
            for (var natRule : resource.getInboundNatRules()) {
                if (!lb.inboundNatRules().containsKey(natRule.getName())) {
                    var protocol = "Tcp".equalsIgnoreCase(natRule.getProtocol()) ? TransportProtocol.TCP :
                                   "Udp".equalsIgnoreCase(natRule.getProtocol()) ? TransportProtocol.UDP :
                                   TransportProtocol.ALL;

                    var natUpdate = update.defineInboundNatRule(natRule.getName())
                            .withProtocol(protocol)
                            .fromFrontend(natRule.getFrontendIpConfigurationName())
                            .fromFrontendPort(natRule.getFrontendPort())
                            .toBackendPort(natRule.getBackendPort());

                    if (natRule.getIdleTimeoutInMinutes() != null) {
                        natUpdate.withIdleTimeoutInMinutes(natRule.getIdleTimeoutInMinutes());
                    }
                    if (natRule.getEnableFloatingIP() != null && natRule.getEnableFloatingIP()) {
                        natUpdate.withFloatingIP(true);
                    }
                    natUpdate.attach();
                }
            }
        }

        lb = update.apply();

        return read(resource);
    }

    @Override
    public boolean delete(LoadBalancerResource resource) {
        if (resource.getId() == null) {
            log.warn("Cannot delete Load Balancer without id");
            return false;
        }

        log.info("Deleting Load Balancer: {}", resource.getId());

        try {
            networkManager.loadBalancers().deleteById(resource.getId());
            log.info("Deleted Load Balancer: {}", resource.getId());
            return true;

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("ResourceNotFound")) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public List<Diagnostic> validate(LoadBalancerResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getName() == null || resource.getName().isBlank()) {
            diagnostics.add(Diagnostic.error("name is required")
                    .withProperty("name"));
        }

        if (resource.getResourceGroup() == null || resource.getResourceGroup().isBlank()) {
            diagnostics.add(Diagnostic.error("resourceGroup is required")
                    .withProperty("resourceGroup"));
        }

        if (resource.getLocation() == null || resource.getLocation().isBlank()) {
            diagnostics.add(Diagnostic.error("location is required")
                    .withProperty("location"));
        }

        if (resource.getSku() != null && !VALID_SKUS.contains(resource.getSku())) {
            diagnostics.add(Diagnostic.error("Invalid sku",
                    "Valid values: " + String.join(", ", VALID_SKUS))
                    .withProperty("sku"));
        }

        if (resource.getTier() != null && !VALID_TIERS.contains(resource.getTier())) {
            diagnostics.add(Diagnostic.error("Invalid tier",
                    "Valid values: " + String.join(", ", VALID_TIERS))
                    .withProperty("tier"));
        }

        // Frontend IP configuration validation
        if (resource.getFrontendIpConfigurations() == null || resource.getFrontendIpConfigurations().isEmpty()) {
            diagnostics.add(Diagnostic.error("At least one frontend IP configuration is required")
                    .withProperty("frontendIpConfigurations"));
        } else {
            for (int i = 0; i < resource.getFrontendIpConfigurations().size(); i++) {
                var frontend = resource.getFrontendIpConfigurations().get(i);
                if (frontend.getName() == null || frontend.getName().isBlank()) {
                    diagnostics.add(Diagnostic.error("Frontend IP configuration name is required")
                            .withProperty("frontendIpConfigurations[" + i + "].name"));
                }
                if (frontend.getPublicIpAddressId() == null && frontend.getSubnetId() == null) {
                    diagnostics.add(Diagnostic.error("Either publicIpAddressId or subnetId is required")
                            .withProperty("frontendIpConfigurations[" + i + "]"));
                }
            }
        }

        // Health probe validation
        if (resource.getHealthProbes() != null) {
            for (int i = 0; i < resource.getHealthProbes().size(); i++) {
                var probe = resource.getHealthProbes().get(i);
                if (probe.getName() == null || probe.getName().isBlank()) {
                    diagnostics.add(Diagnostic.error("Probe name is required")
                            .withProperty("healthProbes[" + i + "].name"));
                }
                if (probe.getProtocol() == null || !VALID_PROBE_PROTOCOLS.contains(probe.getProtocol())) {
                    diagnostics.add(Diagnostic.error("Invalid probe protocol",
                            "Valid values: " + String.join(", ", VALID_PROBE_PROTOCOLS))
                            .withProperty("healthProbes[" + i + "].protocol"));
                }
                if (probe.getPort() == null) {
                    diagnostics.add(Diagnostic.error("Probe port is required")
                            .withProperty("healthProbes[" + i + "].port"));
                }
                if (("Http".equalsIgnoreCase(probe.getProtocol()) || "Https".equalsIgnoreCase(probe.getProtocol()))
                        && (probe.getRequestPath() == null || probe.getRequestPath().isBlank())) {
                    diagnostics.add(Diagnostic.error("requestPath is required for HTTP/HTTPS probes")
                            .withProperty("healthProbes[" + i + "].requestPath"));
                }
            }
        }

        // Load balancing rule validation
        if (resource.getLoadBalancingRules() != null) {
            for (int i = 0; i < resource.getLoadBalancingRules().size(); i++) {
                var rule = resource.getLoadBalancingRules().get(i);
                if (rule.getName() == null || rule.getName().isBlank()) {
                    diagnostics.add(Diagnostic.error("Rule name is required")
                            .withProperty("loadBalancingRules[" + i + "].name"));
                }
                if (rule.getFrontendIpConfigurationName() == null) {
                    diagnostics.add(Diagnostic.error("frontendIpConfigurationName is required")
                            .withProperty("loadBalancingRules[" + i + "].frontendIpConfigurationName"));
                }
                if (rule.getBackendAddressPoolName() == null) {
                    diagnostics.add(Diagnostic.error("backendAddressPoolName is required")
                            .withProperty("loadBalancingRules[" + i + "].backendAddressPoolName"));
                }
                if (rule.getProtocol() == null || !VALID_RULE_PROTOCOLS.contains(rule.getProtocol())) {
                    diagnostics.add(Diagnostic.error("Invalid rule protocol",
                            "Valid values: " + String.join(", ", VALID_RULE_PROTOCOLS))
                            .withProperty("loadBalancingRules[" + i + "].protocol"));
                }
                if (rule.getFrontendPort() == null) {
                    diagnostics.add(Diagnostic.error("frontendPort is required")
                            .withProperty("loadBalancingRules[" + i + "].frontendPort"));
                }
                if (rule.getBackendPort() == null) {
                    diagnostics.add(Diagnostic.error("backendPort is required")
                            .withProperty("loadBalancingRules[" + i + "].backendPort"));
                }
            }
        }

        return diagnostics;
    }

    private LoadBalancerResource mapLoadBalancerToResource(LoadBalancer lb) {
        var resource = new LoadBalancerResource();

        // Input properties
        resource.setName(lb.name());
        resource.setResourceGroup(lb.resourceGroupName());
        resource.setLocation(lb.regionName());

        // SKU - LoadBalancerSkuType is an enum-like class, get string representation
        if (lb.sku() != null) {
            resource.setSku(lb.sku().toString().split("_")[0]); // e.g., "STANDARD_REGIONAL" -> "STANDARD"
        }

        // Frontend IP configurations
        if (lb.frontends() != null) {
            resource.setFrontendIpConfigurations(lb.frontends().values().stream()
                    .map(fe -> {
                        var frontend = LoadBalancerResource.FrontendIpConfiguration.builder()
                                .name(fe.name())
                                .build();

                        if (fe.isPublic()) {
                            var publicFe = (LoadBalancerPublicFrontend) fe;
                            if (publicFe.getPublicIpAddress() != null) {
                                frontend.setPublicIpAddressId(publicFe.getPublicIpAddress().id());
                            }
                        } else {
                            var privateFe = (LoadBalancerPrivateFrontend) fe;
                            // Get subnet ID from inner model
                            if (privateFe.innerModel().subnet() != null) {
                                frontend.setSubnetId(privateFe.innerModel().subnet().id());
                            }
                            frontend.setPrivateIpAddress(privateFe.privateIpAddress());
                            if (privateFe.privateIpAllocationMethod() != null) {
                                frontend.setPrivateIpAllocationMethod(privateFe.privateIpAllocationMethod().toString());
                            }
                        }

                        return frontend;
                    })
                    .collect(Collectors.toList()));
        }

        // Backend address pools
        if (lb.backends() != null) {
            resource.setBackendAddressPools(lb.backends().values().stream()
                    .map(be -> LoadBalancerResource.BackendAddressPool.builder()
                            .name(be.name())
                            .build())
                    .collect(Collectors.toList()));
        }

        // Health probes (combine TCP and HTTP probes)
        var probes = new ArrayList<LoadBalancerResource.HealthProbe>();
        if (lb.tcpProbes() != null) {
            lb.tcpProbes().values().forEach(probe -> probes.add(
                    LoadBalancerResource.HealthProbe.builder()
                            .name(probe.name())
                            .protocol("Tcp")
                            .port(probe.port())
                            .intervalInSeconds(probe.intervalInSeconds())
                            .numberOfProbes(probe.numberOfProbes())
                            .build()));
        }
        if (lb.httpProbes() != null) {
            lb.httpProbes().values().forEach(probe -> probes.add(
                    LoadBalancerResource.HealthProbe.builder()
                            .name(probe.name())
                            .protocol("Http")
                            .port(probe.port())
                            .requestPath(probe.requestPath())
                            .intervalInSeconds(probe.intervalInSeconds())
                            .numberOfProbes(probe.numberOfProbes())
                            .build()));
        }
        if (lb.httpsProbes() != null) {
            lb.httpsProbes().values().forEach(probe -> probes.add(
                    LoadBalancerResource.HealthProbe.builder()
                            .name(probe.name())
                            .protocol("Https")
                            .port(probe.port())
                            .requestPath(probe.requestPath())
                            .intervalInSeconds(probe.intervalInSeconds())
                            .numberOfProbes(probe.numberOfProbes())
                            .build()));
        }
        resource.setHealthProbes(probes);

        // Load balancing rules
        if (lb.loadBalancingRules() != null) {
            resource.setLoadBalancingRules(lb.loadBalancingRules().values().stream()
                    .map(rule -> LoadBalancerResource.LoadBalancingRule.builder()
                            .name(rule.name())
                            .frontendIpConfigurationName(rule.frontend() != null ? rule.frontend().name() : null)
                            .backendAddressPoolName(rule.backend() != null ? rule.backend().name() : null)
                            .probeName(rule.probe() != null ? rule.probe().name() : null)
                            .protocol(rule.protocol() != null ? rule.protocol().toString() : null)
                            .frontendPort(rule.frontendPort())
                            .backendPort(rule.backendPort())
                            .enableFloatingIP(rule.floatingIPEnabled())
                            .idleTimeoutInMinutes(rule.idleTimeoutInMinutes())
                            .loadDistribution(rule.loadDistribution() != null ? rule.loadDistribution().toString() : null)
                            .build())
                    .collect(Collectors.toList()));
        }

        // Inbound NAT rules
        if (lb.inboundNatRules() != null) {
            resource.setInboundNatRules(lb.inboundNatRules().values().stream()
                    .map(rule -> LoadBalancerResource.InboundNatRule.builder()
                            .name(rule.name())
                            .frontendIpConfigurationName(rule.frontend() != null ? rule.frontend().name() : null)
                            .protocol(rule.protocol() != null ? rule.protocol().toString() : null)
                            .frontendPort(rule.frontendPort())
                            .backendPort(rule.backendPort())
                            .enableFloatingIP(rule.floatingIPEnabled())
                            .idleTimeoutInMinutes(rule.idleTimeoutInMinutes())
                            .build())
                    .collect(Collectors.toList()));
        }

        // Tags
        resource.setTags(lb.tags());

        // Cloud-managed properties
        resource.setId(lb.id());
        if (lb.innerModel().provisioningState() != null) {
            resource.setProvisioningState(lb.innerModel().provisioningState().toString());
        }

        return resource;
    }
}
