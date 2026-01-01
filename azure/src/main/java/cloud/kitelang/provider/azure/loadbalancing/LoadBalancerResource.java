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
 *
 * @see <a href="https://learn.microsoft.com/en-us/azure/load-balancer/load-balancer-overview">Azure Load Balancer Documentation</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("LoadBalancer")
public class LoadBalancerResource {

    @Property(description = "The name of the load balancer", optional = false)
    private String name;

    @Property(description = "The resource group name", optional = false)
    private String resourceGroup;

    @Property(description = "The Azure region", optional = false)
    private String location;

    @Property(description = "The SKU of the load balancer",
              validValues = {"Basic", "Standard"})
    private String sku = "Standard";

    @Property(description = "The SKU tier",
              validValues = {"Regional", "Global"})
    private String tier = "Regional";

    @Property(description = "Frontend IP configurations. At least one is required", optional = false)
    private List<FrontendIpConfiguration> frontendIpConfigurations;

    @Property(description = "Backend address pools")
    private List<BackendAddressPool> backendAddressPools;

    @Property(description = "Health probes for backend health monitoring")
    private List<HealthProbe> healthProbes;

    @Property(description = "Load balancing rules")
    private List<LoadBalancingRule> loadBalancingRules;

    @Property(description = "Inbound NAT rules for port forwarding")
    private List<InboundNatRule> inboundNatRules;

    @Property(description = "Outbound rules (Standard SKU only)")
    private List<OutboundRule> outboundRules;

    @Property(description = "Tags to apply to the load balancer")
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    @Cloud(importable = true)
    @Property(description = "The resource ID of the load balancer")
    private String id;

    @Cloud
    @Property(description = "Provisioning state of the load balancer")
    private String provisioningState;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FrontendIpConfiguration {
        @Property(description = "Name of the frontend IP configuration", optional = false)
        private String name;

        @Property(description = "Resource ID of the public IP address. Required for public load balancers")
        private String publicIpAddressId;

        @Property(description = "Resource ID of the subnet for internal load balancers")
        private String subnetId;

        @Property(description = "Private IP address for internal load balancers")
        private String privateIpAddress;

        @Property(description = "Private IP allocation method",
                  validValues = {"Dynamic", "Static"})
        private String privateIpAllocationMethod = "Dynamic";

        @Property(description = "Availability zones")
        private List<String> zones;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BackendAddressPool {
        @Property(description = "Name of the backend address pool", optional = false)
        private String name;

        @Property(description = "Virtual network ID for backend addresses (Standard SKU)")
        private String virtualNetworkId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HealthProbe {
        @Property(description = "Name of the probe", optional = false)
        private String name;

        @Property(description = "Protocol for the probe",
                  validValues = {"Http", "Https", "Tcp"},
                  optional = false)
        private String protocol;

        @Property(description = "Port to probe", optional = false)
        private Integer port;

        @Property(description = "Request path for HTTP/HTTPS probes. Required for HTTP/HTTPS protocol")
        private String requestPath;

        @Property(description = "Interval between probes in seconds")
        private Integer intervalInSeconds = 15;

        @Property(description = "Number of failed probes before marking unhealthy")
        private Integer numberOfProbes = 2;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoadBalancingRule {
        @Property(description = "Name of the rule", optional = false)
        private String name;

        @Property(description = "Name of the frontend IP configuration", optional = false)
        private String frontendIpConfigurationName;

        @Property(description = "Name of the backend address pool", optional = false)
        private String backendAddressPoolName;

        @Property(description = "Name of the health probe")
        private String probeName;

        @Property(description = "Protocol for the rule",
                  validValues = {"Tcp", "Udp", "All"},
                  optional = false)
        private String protocol;

        @Property(description = "Frontend port", optional = false)
        private Integer frontendPort;

        @Property(description = "Backend port", optional = false)
        private Integer backendPort;

        @Property(description = "Enable floating IP (Direct Server Return)")
        private Boolean enableFloatingIP = false;

        @Property(description = "Enable TCP reset for idle connections (Standard SKU)")
        private Boolean enableTcpReset = false;

        @Property(description = "Idle timeout in minutes")
        private Integer idleTimeoutInMinutes = 4;

        @Property(description = "Load distribution mode",
                  validValues = {"Default", "SourceIP", "SourceIPProtocol"})
        private String loadDistribution = "Default";

        @Property(description = "Disable outbound SNAT for this rule")
        private Boolean disableOutboundSnat = false;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InboundNatRule {
        @Property(description = "Name of the NAT rule", optional = false)
        private String name;

        @Property(description = "Name of the frontend IP configuration", optional = false)
        private String frontendIpConfigurationName;

        @Property(description = "Protocol",
                  validValues = {"Tcp", "Udp", "All"},
                  optional = false)
        private String protocol;

        @Property(description = "Frontend port", optional = false)
        private Integer frontendPort;

        @Property(description = "Backend port", optional = false)
        private Integer backendPort;

        @Property(description = "Idle timeout in minutes")
        private Integer idleTimeoutInMinutes = 4;

        @Property(description = "Enable floating IP")
        private Boolean enableFloatingIP = false;

        @Property(description = "Enable TCP reset")
        private Boolean enableTcpReset = false;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OutboundRule {
        @Property(description = "Name of the outbound rule", optional = false)
        private String name;

        @Property(description = "Name of the backend address pool", optional = false)
        private String backendAddressPoolName;

        @Property(description = "Names of the frontend IP configurations", optional = false)
        private List<String> frontendIpConfigurationNames;

        @Property(description = "Protocol",
                  validValues = {"Tcp", "Udp", "All"},
                  optional = false)
        private String protocol;

        @Property(description = "Number of outbound ports per instance")
        private Integer allocatedOutboundPorts;

        @Property(description = "Idle timeout in minutes")
        private Integer idleTimeoutInMinutes = 4;

        @Property(description = "Enable TCP reset")
        private Boolean enableTcpReset = false;
    }
}
