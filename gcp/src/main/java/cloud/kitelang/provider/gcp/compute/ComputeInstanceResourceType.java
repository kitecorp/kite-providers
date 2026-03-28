package cloud.kitelang.provider.gcp.compute;

import cloud.kitelang.provider.Diagnostic;
import cloud.kitelang.provider.ResourceTypeHandler;
import cloud.kitelang.provider.gcp.GcpClientAware;
import com.google.cloud.compute.v1.*;
import com.google.cloud.dns.Dns;
import com.google.cloud.storage.Storage;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * ResourceTypeHandler for GCP Compute Engine Instance.
 * Implements CRUD operations using the GCP Compute Java SDK.
 */
@Slf4j
public class ComputeInstanceResourceType extends ResourceTypeHandler<ComputeInstanceResource> implements GcpClientAware {

    private volatile InstancesClient instancesClient;
    private String project;
    private String defaultZone;

    public ComputeInstanceResourceType() {
        // Client created lazily to pick up configuration
    }

    /** Constructor for testing with a mock client. */
    public ComputeInstanceResourceType(InstancesClient instancesClient, String project, String defaultZone) {
        this.instancesClient = instancesClient;
        this.project = project;
        this.defaultZone = defaultZone;
    }

    @Override
    public void setGcpClients(InstancesClient instancesClient,
                              NetworksClient networksClient,
                              SubnetworksClient subnetworksClient,
                              ForwardingRulesClient forwardingRulesClient,
                              Storage storage,
                              Dns dns) {
        this.instancesClient = instancesClient;
        this.project = System.getProperty("gcp.project");
        this.defaultZone = System.getProperty("gcp.zone", "us-central1-a");
    }

    /**
     * Get or create an InstancesClient.
     * Returns the client injected by the provider, or creates a default one as fallback.
     */
    private InstancesClient getClient() {
        if (instancesClient == null) {
            synchronized (this) {
                if (instancesClient == null) {
                    try {
                        log.debug("Creating InstancesClient with default configuration");
                        instancesClient = InstancesClient.create();
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to create InstancesClient", e);
                    }
                }
            }
        }
        return instancesClient;
    }

    private String getProject() {
        if (project == null) {
            project = System.getProperty("gcp.project");
        }
        return project;
    }

    private String resolveZone(ComputeInstanceResource resource) {
        if (resource.getZone() != null) {
            return resource.getZone();
        }
        return defaultZone != null ? defaultZone : "us-central1-a";
    }

    @Override
    public ComputeInstanceResource create(ComputeInstanceResource resource) {
        var zone = resolveZone(resource);
        log.info("Creating Compute Engine instance '{}' in zone '{}'", resource.getName(), zone);

        // Build the boot disk
        var diskBuilder = AttachedDisk.newBuilder()
                .setAutoDelete(true)
                .setBoot(true)
                .setType("PERSISTENT");

        var initParamsBuilder = AttachedDiskInitializeParams.newBuilder();
        if (resource.getDiskSizeGb() != null) {
            initParamsBuilder.setDiskSizeGb(resource.getDiskSizeGb());
        }
        if (resource.getDiskType() != null) {
            initParamsBuilder.setDiskType(
                    String.format("zones/%s/diskTypes/%s", zone, resource.getDiskType()));
        }

        // Resolve the source image from family + project
        var imageProject = resource.getImageProject() != null ? resource.getImageProject() : "debian-cloud";
        var imageFamily = resource.getImageFamily() != null ? resource.getImageFamily() : "debian-12";
        initParamsBuilder.setSourceImage(
                String.format("projects/%s/global/images/family/%s", imageProject, imageFamily));

        diskBuilder.setInitializeParams(initParamsBuilder.build());

        // Build the network interface
        var networkInterfaceBuilder = NetworkInterface.newBuilder();
        if (resource.getNetworkInterface() != null) {
            networkInterfaceBuilder.setNetwork(
                    String.format("projects/%s/global/networks/%s", getProject(), resource.getNetworkInterface()));
        }
        if (resource.getSubnetwork() != null) {
            networkInterfaceBuilder.setSubnetwork(resource.getSubnetwork());
        }

        // External IP access config
        if (resource.getExternalIp() == null || resource.getExternalIp()) {
            var accessConfig = AccessConfig.newBuilder()
                    .setName("External NAT")
                    .setType("ONE_TO_ONE_NAT")
                    .build();
            networkInterfaceBuilder.addAccessConfigs(accessConfig);
        }

        // Build the instance
        var instanceBuilder = Instance.newBuilder()
                .setName(resource.getName())
                .setMachineType(String.format("zones/%s/machineTypes/%s", zone, resource.getMachineType()))
                .addDisks(diskBuilder.build())
                .addNetworkInterfaces(networkInterfaceBuilder.build());

        // Network tags
        if (resource.getTags() != null && !resource.getTags().isEmpty()) {
            instanceBuilder.setTags(Tags.newBuilder()
                    .addAllItems(resource.getTags())
                    .build());
        }

        // Labels
        if (resource.getLabels() != null && !resource.getLabels().isEmpty()) {
            instanceBuilder.putAllLabels(resource.getLabels());
        }

        // Startup script via metadata
        if (resource.getStartupScript() != null) {
            instanceBuilder.setMetadata(Metadata.newBuilder()
                    .addItems(Items.newBuilder()
                            .setKey("startup-script")
                            .setValue(resource.getStartupScript())
                            .build())
                    .build());
        }

        // Service account
        if (resource.getServiceAccount() != null) {
            var saBuilder = ServiceAccount.newBuilder()
                    .setEmail(resource.getServiceAccount());
            if (resource.getScopes() != null) {
                saBuilder.addAllScopes(resource.getScopes());
            } else {
                saBuilder.addScopes("https://www.googleapis.com/auth/cloud-platform");
            }
            instanceBuilder.addServiceAccounts(saBuilder.build());
        }

        // Preemptible scheduling
        if (resource.getPreemptible() != null && resource.getPreemptible()) {
            instanceBuilder.setScheduling(Scheduling.newBuilder()
                    .setPreemptible(true)
                    .build());
        }

        try {
            var operation = getClient().insertAsync(getProject(), zone, instanceBuilder.build());
            operation.get(); // Wait for completion
            log.info("Created Compute Engine instance: {}", resource.getName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while creating instance", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to create instance: " + e.getCause().getMessage(), e.getCause());
        }

        return read(resource);
    }

    @Override
    public ComputeInstanceResource read(ComputeInstanceResource resource) {
        if (resource.getName() == null) {
            log.warn("Cannot read Compute Engine instance without name");
            return null;
        }

        var zone = resolveZone(resource);
        log.info("Reading Compute Engine instance: {} in zone: {}", resource.getName(), zone);

        try {
            var instance = getClient().get(getProject(), zone, resource.getName());
            return mapToResource(instance, zone);
        } catch (com.google.api.gax.rpc.NotFoundException e) {
            return null;
        }
    }

    @Override
    public ComputeInstanceResource update(ComputeInstanceResource resource) {
        var zone = resolveZone(resource);
        log.info("Updating Compute Engine instance: {} in zone: {}", resource.getName(), zone);

        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("Instance not found: " + resource.getName());
        }

        // Update labels
        if (resource.getLabels() != null) {
            try {
                // Get current instance for fingerprint
                var instance = getClient().get(getProject(), zone, resource.getName());
                var labelsRequest = InstancesSetLabelsRequest.newBuilder()
                        .setLabelFingerprint(instance.getLabelFingerprint())
                        .putAllLabels(resource.getLabels())
                        .build();
                var operation = getClient().setLabelsAsync(getProject(), zone, resource.getName(), labelsRequest);
                operation.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while updating labels", e);
            } catch (ExecutionException e) {
                throw new RuntimeException("Failed to update labels: " + e.getCause().getMessage(), e.getCause());
            }
        }

        // Update tags
        if (resource.getTags() != null) {
            try {
                var instance = getClient().get(getProject(), zone, resource.getName());
                var tagsBuilder = Tags.newBuilder()
                        .addAllItems(resource.getTags())
                        .setFingerprint(instance.getTags().getFingerprint());
                var operation = getClient().setTagsAsync(getProject(), zone, resource.getName(), tagsBuilder.build());
                operation.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while updating tags", e);
            } catch (ExecutionException e) {
                throw new RuntimeException("Failed to update tags: " + e.getCause().getMessage(), e.getCause());
            }
        }

        // Machine type changes require stop/start
        if (resource.getMachineType() != null && !resource.getMachineType().equals(current.getMachineType())) {
            try {
                log.info("Changing machine type requires stop/start for instance {}", resource.getName());

                // Stop instance
                getClient().stopAsync(getProject(), zone, resource.getName()).get();
                waitForStatus(resource.getName(), zone, "TERMINATED");

                // Change machine type
                var machineTypeUri = String.format("zones/%s/machineTypes/%s", zone, resource.getMachineType());
                var machineTypeRequest = InstancesSetMachineTypeRequest.newBuilder()
                        .setMachineType(machineTypeUri)
                        .build();
                getClient().setMachineTypeAsync(getProject(), zone, resource.getName(), machineTypeRequest).get();

                // Start instance
                getClient().startAsync(getProject(), zone, resource.getName()).get();
                waitForStatus(resource.getName(), zone, "RUNNING");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while changing machine type", e);
            } catch (ExecutionException e) {
                throw new RuntimeException("Failed to change machine type: " + e.getCause().getMessage(), e.getCause());
            }
        }

        return read(resource);
    }

    @Override
    public boolean delete(ComputeInstanceResource resource) {
        if (resource.getName() == null) {
            log.warn("Cannot delete Compute Engine instance without name");
            return false;
        }

        var zone = resolveZone(resource);
        log.info("Deleting Compute Engine instance: {} in zone: {}", resource.getName(), zone);

        try {
            var operation = getClient().deleteAsync(getProject(), zone, resource.getName());
            operation.get();
            log.info("Deleted Compute Engine instance: {}", resource.getName());
            return true;
        } catch (com.google.api.gax.rpc.NotFoundException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while deleting instance", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof com.google.api.gax.rpc.NotFoundException) {
                return false;
            }
            throw new RuntimeException("Failed to delete instance: " + e.getCause().getMessage(), e.getCause());
        }
    }

    @Override
    public List<Diagnostic> validate(ComputeInstanceResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getName() == null || resource.getName().isBlank()) {
            diagnostics.add(Diagnostic.error("name is required").withProperty("name"));
        }
        if (resource.getMachineType() == null || resource.getMachineType().isBlank()) {
            diagnostics.add(Diagnostic.error("machineType is required").withProperty("machineType"));
        }
        if (resource.getZone() == null || resource.getZone().isBlank()) {
            diagnostics.add(Diagnostic.error("zone is required").withProperty("zone"));
        }

        if (resource.getDiskType() != null) {
            var validTypes = List.of("pd-standard", "pd-ssd", "pd-balanced");
            if (!validTypes.contains(resource.getDiskType())) {
                diagnostics.add(Diagnostic.error("diskType must be one of: " + String.join(", ", validTypes))
                        .withProperty("diskType"));
            }
        }

        return diagnostics;
    }

    /**
     * Wait for an instance to reach a specific status.
     */
    private void waitForStatus(String instanceName, String zone, String targetStatus) {
        var maxAttempts = 60;
        var attempt = 0;

        while (attempt < maxAttempts) {
            try {
                var instance = getClient().get(getProject(), zone, instanceName);
                if (targetStatus.equals(instance.getStatus())) {
                    log.debug("Instance '{}' reached status '{}'", instanceName, targetStatus);
                    return;
                }
                Thread.sleep(5000);
                attempt++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for instance status", e);
            }
        }

        log.warn("Timed out waiting for instance '{}' to reach status '{}'", instanceName, targetStatus);
    }

    private ComputeInstanceResource mapToResource(Instance instance, String zone) {
        var resource = new ComputeInstanceResource();

        // Input properties
        resource.setName(instance.getName());
        resource.setZone(zone);

        // Extract machine type short name from full URI
        var machineTypeUri = instance.getMachineType();
        if (machineTypeUri.contains("/")) {
            resource.setMachineType(machineTypeUri.substring(machineTypeUri.lastIndexOf('/') + 1));
        } else {
            resource.setMachineType(machineTypeUri);
        }

        // Labels
        if (instance.getLabelsMap() != null && !instance.getLabelsMap().isEmpty()) {
            resource.setLabels(new HashMap<>(instance.getLabelsMap()));
        }

        // Tags
        if (instance.hasTags() && instance.getTags().getItemsList() != null) {
            resource.setTags(new ArrayList<>(instance.getTags().getItemsList()));
        }

        // Network interfaces
        if (!instance.getNetworkInterfacesList().isEmpty()) {
            var nic = instance.getNetworkInterfaces(0);

            // Extract network name from self-link
            var networkUri = nic.getNetwork();
            if (networkUri.contains("/")) {
                resource.setNetworkInterface(networkUri.substring(networkUri.lastIndexOf('/') + 1));
            }

            // Internal IP
            resource.setInternalIpAddress(nic.getNetworkIP());

            // External IP
            if (!nic.getAccessConfigsList().isEmpty()) {
                var externalIp = nic.getAccessConfigs(0).getNatIP();
                if (externalIp != null && !externalIp.isEmpty()) {
                    resource.setExternalIpAddress(externalIp);
                }
            }
        }

        // Cloud-managed properties
        resource.setInstanceId(String.valueOf(instance.getId()));
        resource.setStatus(instance.getStatus());
        resource.setSelfLink(instance.getSelfLink());

        return resource;
    }
}
