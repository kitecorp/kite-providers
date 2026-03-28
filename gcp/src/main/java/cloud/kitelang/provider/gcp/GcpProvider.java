package cloud.kitelang.provider.gcp;

import cloud.kitelang.provider.KiteProvider;
import cloud.kitelang.provider.ProviderServer;
import cloud.kitelang.provider.gcp.compute.ComputeInstanceResourceType;
import cloud.kitelang.provider.gcp.dns.ManagedZoneResourceType;
import cloud.kitelang.provider.gcp.dns.ResourceRecordSetResourceType;
import cloud.kitelang.provider.gcp.loadbalancing.ForwardingRuleResourceType;
import cloud.kitelang.provider.gcp.networking.SubnetworkResourceType;
import cloud.kitelang.provider.gcp.networking.VpcNetworkResourceType;
import cloud.kitelang.provider.gcp.stdlib.*;
import cloud.kitelang.provider.gcp.storage.CloudStorageBucketResourceType;
import com.google.cloud.compute.v1.ForwardingRulesClient;
import com.google.cloud.compute.v1.InstancesClient;
import com.google.cloud.compute.v1.NetworksClient;
import com.google.cloud.compute.v1.SubnetworksClient;
import com.google.cloud.dns.Dns;
import com.google.cloud.dns.DnsOptions;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * GCP Provider for Kite.
 *
 * Provides resources for managing Google Cloud Platform infrastructure:
 * - ComputeInstance: Compute Engine virtual machine
 * - VpcNetwork: VPC Network
 * - Subnetwork: VPC Subnetwork
 * - CloudStorageBucket: Cloud Storage bucket
 * - ForwardingRule: Load Balancer forwarding rule
 * - ManagedZone: Cloud DNS managed zone
 * - ResourceRecordSet: Cloud DNS record set
 *
 * Authentication uses Google Application Default Credentials (ADC):
 * 1. GOOGLE_APPLICATION_CREDENTIALS environment variable
 * 2. gcloud auth application-default login
 * 3. Compute Engine / GKE metadata service
 *
 * Required configuration:
 * - project: Your GCP project ID
 *
 * Example usage in Kite:
 * <pre>
 * provider "gcp" {
 *     project = "my-project-id"
 *     region = "us-central1"
 *     zone = "us-central1-a"
 * }
 *
 * resource ComputeInstance web {
 *     name = "web-server"
 *     machineType = "e2-medium"
 *     zone = "us-central1-a"
 *     imageFamily = "debian-12"
 *     imageProject = "debian-cloud"
 * }
 * </pre>
 */
@Slf4j
public class GcpProvider extends KiteProvider {

    @Getter
    private volatile InstancesClient instancesClient;

    @Getter
    private volatile NetworksClient networksClient;

    @Getter
    private volatile SubnetworksClient subnetworksClient;

    @Getter
    private volatile ForwardingRulesClient forwardingRulesClient;

    @Getter
    private volatile Storage storageClient;

    @Getter
    private volatile Dns dnsClient;

    private String project;
    private String region;
    private String zone;

    public GcpProvider() {
        // Name and version auto-loaded from provider-info.properties
        // Resource types are discovered by the superclass constructor
        registerStandardTypeAdapters();
        log.info("GCP Provider initialized with resources: {}", getResourceTypes().keySet());
    }

    /**
     * Register standard library type adapters for all supported mappings.
     * Each adapter bridges a provider-agnostic stdlib type to its GCP-specific implementation.
     * Must be called after resource types are discovered (handled by superclass constructor).
     */
    private void registerStandardTypeAdapters() {
        var computeHandler = getResourceTypes().get("ComputeInstance");
        if (computeHandler instanceof ComputeInstanceResourceType typed) {
            registerAdapter(new ServerAdapter(typed));
        }

        var vpcHandler = getResourceTypes().get("VpcNetwork");
        if (vpcHandler instanceof VpcNetworkResourceType typed) {
            registerAdapter(new NetworkAdapter(typed));
        }

        var subnetworkHandler = getResourceTypes().get("Subnetwork");
        if (subnetworkHandler instanceof SubnetworkResourceType typed) {
            registerAdapter(new SubnetAdapter(typed));
        }

        var storageHandler = getResourceTypes().get("CloudStorageBucket");
        if (storageHandler instanceof CloudStorageBucketResourceType typed) {
            registerAdapter(new BucketAdapter(typed));
        }

        var forwardingRuleHandler = getResourceTypes().get("ForwardingRule");
        if (forwardingRuleHandler instanceof ForwardingRuleResourceType typed) {
            registerAdapter(new LoadBalancerAdapter(typed));
        }

        var managedZoneHandler = getResourceTypes().get("ManagedZone");
        if (managedZoneHandler instanceof ManagedZoneResourceType typed) {
            registerAdapter(new DnsZoneAdapter(typed));
        }

        var recordSetHandler = getResourceTypes().get("ResourceRecordSet");
        if (recordSetHandler instanceof ResourceRecordSetResourceType typed) {
            registerAdapter(new DnsRecordAdapter(typed));
        }

        log.info("Registered {} standard type adapters", getStandardTypeAdapters().size());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void configure(Object configuration) {
        super.configure(configuration);

        if (configuration instanceof Map<?, ?> config) {
            this.project = (String) config.get("project");
            this.region = (String) config.get("region");
            this.zone = (String) config.get("zone");

            log.info("Configuring GCP provider with project: {}, region: {}, zone: {}",
                    project, region, zone);

            // Set system properties so GCP SDK can pick them up
            if (project != null && !project.isEmpty()) {
                System.setProperty("gcp.project", project);
            }
            if (region != null && !region.isEmpty()) {
                System.setProperty("gcp.region", region);
            }
            if (zone != null && !zone.isEmpty()) {
                System.setProperty("gcp.zone", zone);
            }

            // Create clients and wire them to resource types
            createClients();
            wireClientsToResourceTypes();
        }
    }

    /**
     * Create all GCP SDK clients with the configured project.
     * Uses Application Default Credentials (ADC) for authentication.
     */
    private void createClients() {
        try {
            this.instancesClient = InstancesClient.create();
            this.networksClient = NetworksClient.create();
            this.subnetworksClient = SubnetworksClient.create();
            this.forwardingRulesClient = ForwardingRulesClient.create();

            var storageBuilder = StorageOptions.newBuilder();
            if (project != null) {
                storageBuilder.setProjectId(project);
            }
            this.storageClient = storageBuilder.build().getService();

            var dnsBuilder = DnsOptions.newBuilder();
            if (project != null) {
                dnsBuilder.setProjectId(project);
            }
            this.dnsClient = dnsBuilder.build().getService();

            log.info("GCP clients created successfully");
        } catch (Exception e) {
            log.error("Failed to create GCP clients: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create GCP clients", e);
        }
    }

    /**
     * Push the configured clients to all registered resource type handlers.
     * Each handler that implements {@link GcpClientAware} receives the appropriate client
     * so it does not fall back to creating its own unconfigured client.
     */
    private void wireClientsToResourceTypes() {
        for (var handler : getResourceTypes().values()) {
            if (handler instanceof GcpClientAware aware) {
                aware.setGcpClients(instancesClient, networksClient, subnetworksClient,
                        forwardingRulesClient, storageClient, dnsClient);
            }
        }
        log.debug("Wired GCP clients to {} resource type handlers", getResourceTypes().size());
    }

    /**
     * Get the configured GCP project ID.
     *
     * @return the project ID, or null if not yet configured
     */
    public String getProject() {
        return project;
    }

    /**
     * Get the configured GCP region.
     *
     * @return the region (e.g., "us-central1"), or null if not yet configured
     */
    public String getRegion() {
        return region;
    }

    /**
     * Get the configured GCP zone.
     *
     * @return the zone (e.g., "us-central1-a"), or null if not yet configured
     */
    public String getZone() {
        return zone;
    }

    @Override
    public void stop() {
        super.stop();
        // Close GCP clients that implement AutoCloseable
        closeQuietly(instancesClient);
        closeQuietly(networksClient);
        closeQuietly(subnetworksClient);
        closeQuietly(forwardingRulesClient);
        // Storage and Dns clients are managed by their options
    }

    private void closeQuietly(AutoCloseable client) {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.debug("Error closing GCP client: {}", e.getMessage());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        configureLogging();
        log.info("Starting GCP Provider...");
        ProviderServer.serve(new GcpProvider());
    }

    /**
     * Configure JUL logging to minimize output.
     * The engine captures stderr and logs it, so we want to reduce noise.
     */
    private static void configureLogging() {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%4$s: %5$s%6$s%n");

        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.WARNING);

        for (var handler : rootLogger.getHandlers()) {
            rootLogger.removeHandler(handler);
        }

        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.WARNING);
        handler.setFormatter(new SimpleFormatter());
        rootLogger.addHandler(handler);
    }
}
