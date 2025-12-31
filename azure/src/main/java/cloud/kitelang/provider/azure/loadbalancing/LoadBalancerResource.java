package cloud.kitelang.provider.azure.loadbalancing;

import cloud.kitelang.api.annotations.Cloud;
import cloud.kitelang.api.annotations.Property;
import cloud.kitelang.api.annotations.TypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Azure Load Balancer resource with integrated frontend, backend, probes, and rules.
 *
 * Example usage:
 * <pre>
 * resource LoadBalancer web {
 *     name = "web-lb"
 *     resourceGroup = rg.name
 *     location = "eastus"
 *     sku = "Standard"
 *
 *     frontendIpConfigurations = [{
 *         name: "public-frontend",
 *         publicIpAddressId: pip.id
 *     }]
 *
 *     backendAddressPools = [{
 *         name: "web-servers"
 *     }]
 *
 *     healthProbes = [{
 *         name: "http-probe",
 *         protocol: "Http",
 *         port: 80,
 *         requestPath: "/health",
 *         intervalInSeconds: 15,
 *         numberOfProbes: 2
 *     }]
 *
 *     loadBalancingRules = [{
 *         name: "http-rule",
 *         frontendIpConfigurationName: "public-frontend",
 *         backendAddressPoolName: "web-servers",
 *         probeName: "http-probe",
 *         protocol: "Tcp",
 *         frontendPort: 80,
 *         backendPort: 80,
 *         enableFloatingIP: false,
 *         idleTimeoutInMinutes: 4
 *     }]
 *
 *     tags = {
 *         Environment: "production"
 *     }
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("LoadBalancer")
public class LoadBalancerResource {

    /**
     * The name of the load balancer.
     * Required.
     */
    @Property
    private String name;

    /**
     * The resource group name.
     * Required.
     */
    @Property
    private String resourceGroup;

    /**
     * The Azure region.
     * Required.
     */
    @Property
    private String location;

    /**
     * The SKU of the load balancer.
     * Valid values: Basic, Standard.
     * Default: Standard
     */
    @Property
    private String sku;

    /**
     * The SKU tier.
     * Valid values: Regional, Global.
     * Default: Regional
     */
    @Property
    private String tier;

    /**
     * Frontend IP configurations.
     * At least one is required.
     */
    @Property
    private List<FrontendIpConfiguration> frontendIpConfigurations;

    /**
     * Backend address pools.
     */
    @Property
    private List<BackendAddressPool> backendAddressPools;

    /**
     * Health probes for backend health monitoring.
     */
    @Property
    private List<HealthProbe> healthProbes;

    /**
     * Load balancing rules.
     */
    @Property
    private List<LoadBalancingRule> loadBalancingRules;

    /**
     * Inbound NAT rules for port forwarding.
     */
    @Property
    private List<InboundNatRule> inboundNatRules;

    /**
     * Outbound rules (Standard SKU only).
     */
    @Property
    private List<OutboundRule> outboundRules;

    /**
     * Tags to apply to the load balancer.
     */
    @Property
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    /**
     * The resource ID of the load balancer.
     */
    @Cloud
    @Property
    private String id;

    /**
     * Provisioning state of the load balancer.
     */
    @Cloud
    @Property
    private String provisioningState;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FrontendIpConfiguration {
        /**
         * Name of the frontend IP configuration.
         * Required.
         */
        private String name;

        /**
         * Resource ID of the public IP address.
         * Required for public load balancers.
         */
        private String publicIpAddressId;

        /**
         * Resource ID of the subnet for internal load balancers.
         */
        private String subnetId;

        /**
         * Private IP address for internal load balancers.
         */
        private String privateIpAddress;

        /**
         * Private IP allocation method.
         * Valid values: Dynamic, Static.
         * Default: Dynamic
         */
        private String privateIpAllocationMethod;

        /**
         * Availability zones.
         */
        private List<String> zones;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BackendAddressPool {
        /**
         * Name of the backend address pool.
         * Required.
         */
        private String name;

        /**
         * Virtual network ID for backend addresses (Standard SKU).
         */
        private String virtualNetworkId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HealthProbe {
        /**
         * Name of the probe.
         * Required.
         */
        private String name;

        /**
         * Protocol for the probe.
         * Valid values: Http, Https, Tcp.
         * Required.
         */
        private String protocol;

        /**
         * Port to probe.
         * Required.
         */
        private Integer port;

        /**
         * Request path for HTTP/HTTPS probes.
         * Required for HTTP/HTTPS protocol.
         */
        private String requestPath;

        /**
         * Interval between probes in seconds.
         * Default: 15
         */
        private Integer intervalInSeconds;

        /**
         * Number of failed probes before marking unhealthy.
         * Default: 2
         */
        private Integer numberOfProbes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoadBalancingRule {
        /**
         * Name of the rule.
         * Required.
         */
        private String name;

        /**
         * Name of the frontend IP configuration.
         * Required.
         */
        private String frontendIpConfigurationName;

        /**
         * Name of the backend address pool.
         * Required.
         */
        private String backendAddressPoolName;

        /**
         * Name of the health probe.
         */
        private String probeName;

        /**
         * Protocol for the rule.
         * Valid values: Tcp, Udp, All.
         * Required.
         */
        private String protocol;

        /**
         * Frontend port.
         * Required.
         */
        private Integer frontendPort;

        /**
         * Backend port.
         * Required.
         */
        private Integer backendPort;

        /**
         * Enable floating IP (Direct Server Return).
         * Default: false
         */
        private Boolean enableFloatingIP;

        /**
         * Enable TCP reset for idle connections (Standard SKU).
         * Default: false
         */
        private Boolean enableTcpReset;

        /**
         * Idle timeout in minutes.
         * Default: 4
         */
        private Integer idleTimeoutInMinutes;

        /**
         * Load distribution mode.
         * Valid values: Default, SourceIP, SourceIPProtocol.
         * Default: Default
         */
        private String loadDistribution;

        /**
         * Disable outbound SNAT for this rule.
         * Default: false
         */
        private Boolean disableOutboundSnat;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InboundNatRule {
        /**
         * Name of the NAT rule.
         * Required.
         */
        private String name;

        /**
         * Name of the frontend IP configuration.
         * Required.
         */
        private String frontendIpConfigurationName;

        /**
         * Protocol.
         * Valid values: Tcp, Udp, All.
         * Required.
         */
        private String protocol;

        /**
         * Frontend port.
         * Required.
         */
        private Integer frontendPort;

        /**
         * Backend port.
         * Required.
         */
        private Integer backendPort;

        /**
         * Idle timeout in minutes.
         * Default: 4
         */
        private Integer idleTimeoutInMinutes;

        /**
         * Enable floating IP.
         * Default: false
         */
        private Boolean enableFloatingIP;

        /**
         * Enable TCP reset.
         * Default: false
         */
        private Boolean enableTcpReset;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OutboundRule {
        /**
         * Name of the outbound rule.
         * Required.
         */
        private String name;

        /**
         * Name of the backend address pool.
         * Required.
         */
        private String backendAddressPoolName;

        /**
         * Names of the frontend IP configurations.
         * Required.
         */
        private List<String> frontendIpConfigurationNames;

        /**
         * Protocol.
         * Valid values: Tcp, Udp, All.
         * Required.
         */
        private String protocol;

        /**
         * Number of outbound ports per instance.
         */
        private Integer allocatedOutboundPorts;

        /**
         * Idle timeout in minutes.
         * Default: 4
         */
        private Integer idleTimeoutInMinutes;

        /**
         * Enable TCP reset.
         * Default: false
         */
        private Boolean enableTcpReset;
    }
}
