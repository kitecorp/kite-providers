package cloud.kitelang.provider.gcp.networking;

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
import java.util.concurrent.ExecutionException;

/**
 * ResourceTypeHandler for GCP VPC Network.
 * Implements CRUD operations using the GCP Compute Java SDK.
 */
@Slf4j
public class VpcNetworkResourceType extends ResourceTypeHandler<VpcNetworkResource> implements GcpClientAware {

    private volatile NetworksClient networksClient;
    private String project;

    public VpcNetworkResourceType() {
        // Client created lazily to pick up configuration
    }

    /** Constructor for testing with a mock client. */
    public VpcNetworkResourceType(NetworksClient networksClient, String project) {
        this.networksClient = networksClient;
        this.project = project;
    }

    @Override
    public void setGcpClients(InstancesClient instancesClient,
                              NetworksClient networksClient,
                              SubnetworksClient subnetworksClient,
                              ForwardingRulesClient forwardingRulesClient,
                              Storage storage,
                              Dns dns) {
        this.networksClient = networksClient;
        this.project = System.getProperty("gcp.project");
    }

    private NetworksClient getClient() {
        if (networksClient == null) {
            synchronized (this) {
                if (networksClient == null) {
                    try {
                        log.debug("Creating NetworksClient with default configuration");
                        networksClient = NetworksClient.create();
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to create NetworksClient", e);
                    }
                }
            }
        }
        return networksClient;
    }

    private String getProject() {
        if (project == null) {
            project = System.getProperty("gcp.project");
        }
        return project;
    }

    @Override
    public VpcNetworkResource create(VpcNetworkResource resource) {
        log.info("Creating VPC Network: {}", resource.getName());

        var networkBuilder = Network.newBuilder()
                .setName(resource.getName())
                .setAutoCreateSubnetworks(
                        resource.getAutoCreateSubnetworks() != null ? resource.getAutoCreateSubnetworks() : true);

        if (resource.getRoutingMode() != null) {
            networkBuilder.setRoutingConfig(NetworkRoutingConfig.newBuilder()
                    .setRoutingMode(resource.getRoutingMode())
                    .build());
        }

        if (resource.getDescription() != null) {
            networkBuilder.setDescription(resource.getDescription());
        }

        try {
            var operation = getClient().insertAsync(getProject(), networkBuilder.build());
            operation.get();
            log.info("Created VPC Network: {}", resource.getName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while creating VPC network", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to create VPC network: " + e.getCause().getMessage(), e.getCause());
        }

        return read(resource);
    }

    @Override
    public VpcNetworkResource read(VpcNetworkResource resource) {
        if (resource.getName() == null) {
            log.warn("Cannot read VPC Network without name");
            return null;
        }

        log.info("Reading VPC Network: {}", resource.getName());

        try {
            var network = getClient().get(getProject(), resource.getName());
            return mapToResource(network);
        } catch (com.google.api.gax.rpc.NotFoundException e) {
            return null;
        }
    }

    @Override
    public VpcNetworkResource update(VpcNetworkResource resource) {
        log.info("Updating VPC Network: {}", resource.getName());

        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("VPC Network not found: " + resource.getName());
        }

        // VPC network updates are limited; routing mode can be patched
        var networkBuilder = Network.newBuilder()
                .setName(resource.getName());

        if (resource.getRoutingMode() != null) {
            networkBuilder.setRoutingConfig(NetworkRoutingConfig.newBuilder()
                    .setRoutingMode(resource.getRoutingMode())
                    .build());
        }

        try {
            var operation = getClient().patchAsync(getProject(), resource.getName(), networkBuilder.build());
            operation.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while updating VPC network", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to update VPC network: " + e.getCause().getMessage(), e.getCause());
        }

        return read(resource);
    }

    @Override
    public boolean delete(VpcNetworkResource resource) {
        if (resource.getName() == null) {
            log.warn("Cannot delete VPC Network without name");
            return false;
        }

        log.info("Deleting VPC Network: {}", resource.getName());

        try {
            var operation = getClient().deleteAsync(getProject(), resource.getName());
            operation.get();
            log.info("Deleted VPC Network: {}", resource.getName());
            return true;
        } catch (com.google.api.gax.rpc.NotFoundException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while deleting VPC network", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof com.google.api.gax.rpc.NotFoundException) {
                return false;
            }
            throw new RuntimeException("Failed to delete VPC network: " + e.getCause().getMessage(), e.getCause());
        }
    }

    @Override
    public List<Diagnostic> validate(VpcNetworkResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getName() == null || resource.getName().isBlank()) {
            diagnostics.add(Diagnostic.error("name is required").withProperty("name"));
        }

        if (resource.getRoutingMode() != null) {
            if (!"REGIONAL".equals(resource.getRoutingMode()) &&
                !"GLOBAL".equals(resource.getRoutingMode())) {
                diagnostics.add(Diagnostic.error("routingMode must be 'REGIONAL' or 'GLOBAL'")
                        .withProperty("routingMode"));
            }
        }

        return diagnostics;
    }

    private VpcNetworkResource mapToResource(Network network) {
        var resource = new VpcNetworkResource();

        resource.setName(network.getName());
        resource.setAutoCreateSubnetworks(network.getAutoCreateSubnetworks());
        resource.setDescription(network.getDescription());

        if (network.hasRoutingConfig()) {
            resource.setRoutingMode(network.getRoutingConfig().getRoutingMode());
        }

        // Cloud-managed properties
        resource.setNetworkId(String.valueOf(network.getId()));
        resource.setSelfLink(network.getSelfLink());
        resource.setCreationTimestamp(network.getCreationTimestamp());

        return resource;
    }
}
