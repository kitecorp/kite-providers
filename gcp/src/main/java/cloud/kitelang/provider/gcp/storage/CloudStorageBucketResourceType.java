package cloud.kitelang.provider.gcp.storage;

import cloud.kitelang.provider.Diagnostic;
import cloud.kitelang.provider.ResourceTypeHandler;
import cloud.kitelang.provider.gcp.GcpClientAware;
import com.google.cloud.compute.v1.ForwardingRulesClient;
import com.google.cloud.compute.v1.InstancesClient;
import com.google.cloud.compute.v1.NetworksClient;
import com.google.cloud.compute.v1.SubnetworksClient;
import com.google.cloud.dns.Dns;
import com.google.cloud.storage.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ResourceTypeHandler for GCP Cloud Storage Bucket.
 * Implements CRUD operations using the GCP Cloud Storage Java SDK.
 */
@Slf4j
public class CloudStorageBucketResourceType extends ResourceTypeHandler<CloudStorageBucketResource> implements GcpClientAware {

    private static final Set<String> VALID_STORAGE_CLASSES = Set.of(
            "STANDARD", "NEARLINE", "COLDLINE", "ARCHIVE"
    );

    private volatile Storage storageClient;
    private String project;

    public CloudStorageBucketResourceType() {
        // Client created lazily to pick up configuration
    }

    /** Constructor for testing with a mock client. */
    public CloudStorageBucketResourceType(Storage storageClient, String project) {
        this.storageClient = storageClient;
        this.project = project;
    }

    @Override
    public void setGcpClients(InstancesClient instancesClient,
                              NetworksClient networksClient,
                              SubnetworksClient subnetworksClient,
                              ForwardingRulesClient forwardingRulesClient,
                              Storage storage,
                              Dns dns) {
        this.storageClient = storage;
        this.project = System.getProperty("gcp.project");
    }

    private Storage getClient() {
        if (storageClient == null) {
            synchronized (this) {
                if (storageClient == null) {
                    log.debug("Creating Storage client with default configuration");
                    storageClient = StorageOptions.getDefaultInstance().getService();
                }
            }
        }
        return storageClient;
    }

    private String getProject() {
        if (project == null) {
            project = System.getProperty("gcp.project");
        }
        return project;
    }

    @Override
    public CloudStorageBucketResource create(CloudStorageBucketResource resource) {
        log.info("Creating Cloud Storage Bucket: {}", resource.getName());

        var bucketInfoBuilder = BucketInfo.newBuilder(resource.getName());

        // Location
        if (resource.getLocation() != null) {
            bucketInfoBuilder.setLocation(resource.getLocation());
        }

        // Storage class
        if (resource.getStorageClass() != null) {
            bucketInfoBuilder.setStorageClass(StorageClass.valueOf(resource.getStorageClass()));
        }

        // Versioning
        if (resource.getVersioning() != null) {
            bucketInfoBuilder.setVersioningEnabled(resource.getVersioning());
        }

        // Labels
        if (resource.getLabels() != null && !resource.getLabels().isEmpty()) {
            bucketInfoBuilder.setLabels(resource.getLabels());
        }

        // Uniform bucket-level access
        if (resource.getUniformBucketLevelAccess() != null && resource.getUniformBucketLevelAccess()) {
            bucketInfoBuilder.setIamConfiguration(BucketInfo.IamConfiguration.newBuilder()
                    .setIsUniformBucketLevelAccessEnabled(true)
                    .build());
        }

        var bucket = getClient().create(bucketInfoBuilder.build());
        log.info("Created Cloud Storage Bucket: {}", bucket.getName());

        return read(resource);
    }

    @Override
    public CloudStorageBucketResource read(CloudStorageBucketResource resource) {
        if (resource.getName() == null) {
            log.warn("Cannot read Cloud Storage Bucket without name");
            return null;
        }

        log.info("Reading Cloud Storage Bucket: {}", resource.getName());

        var bucket = getClient().get(resource.getName());
        if (bucket == null) {
            return null;
        }

        return mapToResource(bucket);
    }

    @Override
    public CloudStorageBucketResource update(CloudStorageBucketResource resource) {
        log.info("Updating Cloud Storage Bucket: {}", resource.getName());

        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("Cloud Storage Bucket not found: " + resource.getName());
        }

        var bucketInfoBuilder = BucketInfo.newBuilder(resource.getName());

        // Versioning
        if (resource.getVersioning() != null) {
            bucketInfoBuilder.setVersioningEnabled(resource.getVersioning());
        }

        // Labels
        if (resource.getLabels() != null) {
            bucketInfoBuilder.setLabels(resource.getLabels());
        }

        // Storage class (can be changed)
        if (resource.getStorageClass() != null) {
            bucketInfoBuilder.setStorageClass(StorageClass.valueOf(resource.getStorageClass()));
        }

        getClient().update(bucketInfoBuilder.build());

        return read(resource);
    }

    @Override
    public boolean delete(CloudStorageBucketResource resource) {
        if (resource.getName() == null) {
            log.warn("Cannot delete Cloud Storage Bucket without name");
            return false;
        }

        log.info("Deleting Cloud Storage Bucket: {}", resource.getName());

        try {
            // Delete all objects first (bucket must be empty)
            deleteAllObjects(resource.getName());

            var deleted = getClient().get(resource.getName()).delete();
            if (deleted) {
                log.info("Deleted Cloud Storage Bucket: {}", resource.getName());
            }
            return deleted;
        } catch (StorageException e) {
            if (e.getCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public List<Diagnostic> validate(CloudStorageBucketResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getName() == null || resource.getName().isBlank()) {
            diagnostics.add(Diagnostic.error("name is required").withProperty("name"));
        } else {
            // GCS bucket naming rules
            if (!resource.getName().matches("^[a-z0-9][a-z0-9._-]{1,220}[a-z0-9]$")) {
                diagnostics.add(Diagnostic.error("Invalid bucket name format",
                        "Bucket names must be 3-222 characters, lowercase, and may contain dots, hyphens, and underscores")
                        .withProperty("name"));
            }
        }

        if (resource.getStorageClass() != null && !VALID_STORAGE_CLASSES.contains(resource.getStorageClass())) {
            diagnostics.add(Diagnostic.error("Invalid storage class",
                    "Valid values: " + String.join(", ", VALID_STORAGE_CLASSES))
                    .withProperty("storageClass"));
        }

        return diagnostics;
    }

    private void deleteAllObjects(String bucketName) {
        var blobs = getClient().list(bucketName);
        for (var blob : blobs.iterateAll()) {
            blob.delete();
        }
    }

    private CloudStorageBucketResource mapToResource(Bucket bucket) {
        var resource = new CloudStorageBucketResource();

        resource.setName(bucket.getName());
        resource.setLocation(bucket.getLocation());

        if (bucket.getStorageClass() != null) {
            resource.setStorageClass(bucket.getStorageClass().name());
        }

        resource.setVersioning(bucket.versioningEnabled());

        if (bucket.getLabels() != null && !bucket.getLabels().isEmpty()) {
            resource.setLabels(new HashMap<>(bucket.getLabels()));
        }

        // Cloud-managed properties
        resource.setSelfLink(bucket.getSelfLink());
        if (bucket.getCreateTimeOffsetDateTime() != null) {
            resource.setTimeCreated(bucket.getCreateTimeOffsetDateTime().toString());
        }

        return resource;
    }
}
