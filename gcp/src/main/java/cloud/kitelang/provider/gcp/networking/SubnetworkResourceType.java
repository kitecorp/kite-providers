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
 * ResourceTypeHandler for GCP Subnetwork.
 * Implements CRUD operations using the GCP Compute Java SDK.
 */
@Slf4j
public class SubnetworkResourceType extends ResourceTypeHandler<SubnetworkResource> implements GcpClientAware {

    private volatile SubnetworksClient subnetworksClient;
    private String project;
    private String defaultRegion;

    public SubnetworkResourceType() {
        // Client created lazily to pick up configuration
    }

    /** Constructor for testing with a mock client. */
    public SubnetworkResourceType(SubnetworksClient subnetworksClient, String project, String defaultRegion) {
        this.subnetworksClient = subnetworksClient;
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
        this.subnetworksClient = subnetworksClient;
        this.project = System.getProperty("gcp.project");
        this.defaultRegion = System.getProperty("gcp.region", "us-central1");
    }

    private SubnetworksClient getClient() {
        if (subnetworksClient == null) {
            synchronized (this) {
                if (subnetworksClient == null) {
                    try {
                        log.debug("Creating SubnetworksClient with default configuration");
                        subnetworksClient = SubnetworksClient.create();
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to create SubnetworksClient", e);
                    }
                }
            }
        }
        return subnetworksClient;
    }

    private String getProject() {
        if (project == null) {
            project = System.getProperty("gcp.project");
        }
        return project;
    }

    private String resolveRegion(SubnetworkResource resource) {
        if (resource.getRegion() != null) {
            return resource.getRegion();
        }
        return defaultRegion != null ? defaultRegion : "us-central1";
    }

    @Override
    public SubnetworkResource create(SubnetworkResource resource) {
        var region = resolveRegion(resource);
        log.info("Creating Subnetwork '{}' in region '{}'", resource.getName(), region);

        var subnetBuilder = Subnetwork.newBuilder()
                .setName(resource.getName())
                .setIpCidrRange(resource.getIpCidrRange())
                .setRegion(region);

        // Resolve network reference
        if (resource.getNetwork() != null) {
            var networkRef = resource.getNetwork();
            if (!networkRef.startsWith("projects/") && !networkRef.startsWith("https://")) {
                networkRef = String.format("projects/%s/global/networks/%s", getProject(), networkRef);
            }
            subnetBuilder.setNetwork(networkRef);
        }

        if (resource.getPrivateIpGoogleAccess() != null) {
            subnetBuilder.setPrivateIpGoogleAccess(resource.getPrivateIpGoogleAccess());
        }

        if (resource.getDescription() != null) {
            subnetBuilder.setDescription(resource.getDescription());
        }

        try {
            var operation = getClient().insertAsync(getProject(), region, subnetBuilder.build());
            operation.get();
            log.info("Created Subnetwork: {}", resource.getName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while creating subnetwork", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to create subnetwork: " + e.getCause().getMessage(), e.getCause());
        }

        return read(resource);
    }

    @Override
    public SubnetworkResource read(SubnetworkResource resource) {
        if (resource.getName() == null) {
            log.warn("Cannot read Subnetwork without name");
            return null;
        }

        var region = resolveRegion(resource);
        log.info("Reading Subnetwork: {} in region: {}", resource.getName(), region);

        try {
            var subnetwork = getClient().get(getProject(), region, resource.getName());
            return mapToResource(subnetwork, region);
        } catch (com.google.api.gax.rpc.NotFoundException e) {
            return null;
        }
    }

    @Override
    public SubnetworkResource update(SubnetworkResource resource) {
        var region = resolveRegion(resource);
        log.info("Updating Subnetwork: {} in region: {}", resource.getName(), region);

        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("Subnetwork not found: " + resource.getName());
        }

        // Subnetwork updates support expanding the IP range and toggling private Google access
        var subnetBuilder = Subnetwork.newBuilder();

        if (resource.getPrivateIpGoogleAccess() != null) {
            subnetBuilder.setPrivateIpGoogleAccess(resource.getPrivateIpGoogleAccess());
        }

        // IP range can only be expanded, not shrunk
        if (resource.getIpCidrRange() != null) {
            subnetBuilder.setIpCidrRange(resource.getIpCidrRange());
        }

        try {
            var operation = getClient().patchAsync(getProject(), region, resource.getName(), subnetBuilder.build());
            operation.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while updating subnetwork", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to update subnetwork: " + e.getCause().getMessage(), e.getCause());
        }

        return read(resource);
    }

    @Override
    public boolean delete(SubnetworkResource resource) {
        if (resource.getName() == null) {
            log.warn("Cannot delete Subnetwork without name");
            return false;
        }

        var region = resolveRegion(resource);
        log.info("Deleting Subnetwork: {} in region: {}", resource.getName(), region);

        try {
            var operation = getClient().deleteAsync(getProject(), region, resource.getName());
            operation.get();
            log.info("Deleted Subnetwork: {}", resource.getName());
            return true;
        } catch (com.google.api.gax.rpc.NotFoundException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while deleting subnetwork", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof com.google.api.gax.rpc.NotFoundException) {
                return false;
            }
            throw new RuntimeException("Failed to delete subnetwork: " + e.getCause().getMessage(), e.getCause());
        }
    }

    @Override
    public List<Diagnostic> validate(SubnetworkResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getName() == null || resource.getName().isBlank()) {
            diagnostics.add(Diagnostic.error("name is required").withProperty("name"));
        }
        if (resource.getNetwork() == null || resource.getNetwork().isBlank()) {
            diagnostics.add(Diagnostic.error("network is required").withProperty("network"));
        }
        if (resource.getIpCidrRange() == null || resource.getIpCidrRange().isBlank()) {
            diagnostics.add(Diagnostic.error("ipCidrRange is required").withProperty("ipCidrRange"));
        } else if (!resource.getIpCidrRange().matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}/\\d{1,2}$")) {
            diagnostics.add(Diagnostic.error("Invalid CIDR block format",
                    "Expected format: x.x.x.x/x (e.g., 10.0.1.0/24)")
                    .withProperty("ipCidrRange"));
        }

        return diagnostics;
    }

    private SubnetworkResource mapToResource(Subnetwork subnetwork, String region) {
        var resource = new SubnetworkResource();

        resource.setName(subnetwork.getName());
        resource.setIpCidrRange(subnetwork.getIpCidrRange());
        resource.setRegion(region);
        resource.setPrivateIpGoogleAccess(subnetwork.getPrivateIpGoogleAccess());
        resource.setDescription(subnetwork.getDescription());

        // Extract network name from self-link
        var networkUri = subnetwork.getNetwork();
        if (networkUri != null && networkUri.contains("/")) {
            resource.setNetwork(networkUri.substring(networkUri.lastIndexOf('/') + 1));
        } else {
            resource.setNetwork(networkUri);
        }

        // Cloud-managed properties
        resource.setSubnetworkId(String.valueOf(subnetwork.getId()));
        resource.setSelfLink(subnetwork.getSelfLink());
        resource.setGatewayAddress(subnetwork.getGatewayAddress());

        return resource;
    }
}
