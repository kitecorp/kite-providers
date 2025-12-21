package cloud.kitelang.provider.azure;

import cloud.kitelang.provider.Diagnostic;
import cloud.kitelang.provider.ResourceTypeHandler;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.storage.StorageManager;
import com.azure.resourcemanager.storage.models.BlobContainer;
import com.azure.resourcemanager.storage.models.PublicAccess;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * ResourceTypeHandler for Azure Blob Container.
 * Implements CRUD operations using Azure Storage SDK.
 */
@Slf4j
public class BlobContainerResourceType extends ResourceTypeHandler<BlobContainerResource> {

    private static final Set<String> VALID_PUBLIC_ACCESS = Set.of("None", "Blob", "Container");

    private final StorageManager storageManager;

    public BlobContainerResourceType() {
        var credential = new DefaultAzureCredentialBuilder().build();

        String subscriptionId = System.getenv("AZURE_SUBSCRIPTION_ID");
        if (subscriptionId == null || subscriptionId.isBlank()) {
            throw new IllegalStateException(
                    "AZURE_SUBSCRIPTION_ID environment variable must be set");
        }

        String tenantId = System.getenv("AZURE_TENANT_ID");
        var profile = new AzureProfile(tenantId, subscriptionId, AzureEnvironment.AZURE);

        this.storageManager = StorageManager.authenticate(credential, profile);
    }

    public BlobContainerResourceType(StorageManager storageManager) {
        this.storageManager = storageManager;
    }

    @Override
    public BlobContainerResource create(BlobContainerResource resource) {
        log.info("Creating Blob Container: {} in {}", resource.getName(), resource.getStorageAccountName());

        // Determine public access level
        var publicAccess = resource.getPublicAccess() != null
                ? PublicAccess.fromString(resource.getPublicAccess().toUpperCase())
                : PublicAccess.NONE;

        // Build container with public access
        var withPublicAccess = storageManager.blobContainers()
                .defineContainer(resource.getName())
                .withExistingStorageAccount(resource.getResourceGroup(), resource.getStorageAccountName())
                .withPublicAccess(publicAccess);

        // Add metadata if specified
        BlobContainer container;
        if (resource.getMetadata() != null && !resource.getMetadata().isEmpty()) {
            container = withPublicAccess.withMetadata(resource.getMetadata()).create();
        } else {
            container = withPublicAccess.create();
        }

        log.info("Created Blob Container: {}", container.id());

        return read(resource);
    }

    @Override
    public BlobContainerResource read(BlobContainerResource resource) {
        if (resource.getName() == null || resource.getStorageAccountName() == null ||
            resource.getResourceGroup() == null) {
            log.warn("Cannot read Blob Container without name, storageAccountName, and resourceGroup");
            return null;
        }

        log.info("Reading Blob Container: {}", resource.getName());

        try {
            var container = storageManager.blobContainers()
                    .get(resource.getResourceGroup(), resource.getStorageAccountName(), resource.getName());

            if (container == null) {
                return null;
            }

            return mapContainerToResource(container, resource.getStorageAccountName(), resource.getResourceGroup());

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("ContainerNotFound")) {
                return null;
            }
            if (e.getMessage() != null && e.getMessage().contains("ResourceNotFound")) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public BlobContainerResource update(BlobContainerResource resource) {
        log.info("Updating Blob Container: {}", resource.getName());

        var container = storageManager.blobContainers()
                .get(resource.getResourceGroup(), resource.getStorageAccountName(), resource.getName());

        if (container == null) {
            throw new RuntimeException("Blob Container not found: " + resource.getName());
        }

        var update = container.update();

        // Update public access
        if (resource.getPublicAccess() != null) {
            update = update.withPublicAccess(PublicAccess.fromString(resource.getPublicAccess().toUpperCase()));
        }

        // Update metadata
        if (resource.getMetadata() != null) {
            update = update.withMetadata(resource.getMetadata());
        }

        update.apply();

        return read(resource);
    }

    @Override
    public boolean delete(BlobContainerResource resource) {
        if (resource.getName() == null || resource.getStorageAccountName() == null ||
            resource.getResourceGroup() == null) {
            log.warn("Cannot delete Blob Container without name, storageAccountName, and resourceGroup");
            return false;
        }

        log.info("Deleting Blob Container: {}", resource.getName());

        try {
            storageManager.blobContainers()
                    .delete(resource.getResourceGroup(), resource.getStorageAccountName(), resource.getName());

            log.info("Deleted Blob Container: {}", resource.getName());
            return true;

        } catch (Exception e) {
            if (e.getMessage() != null &&
                (e.getMessage().contains("ContainerNotFound") || e.getMessage().contains("ResourceNotFound"))) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public List<Diagnostic> validate(BlobContainerResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getName() == null || resource.getName().isBlank()) {
            diagnostics.add(Diagnostic.error("name is required")
                    .withProperty("name"));
        } else {
            // Validate container name format
            if (!resource.getName().matches("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$") ||
                resource.getName().length() < 3 || resource.getName().length() > 63) {
                diagnostics.add(Diagnostic.error("Invalid container name",
                        "Name must be 3-63 characters, lowercase letters, numbers, and hyphens")
                        .withProperty("name"));
            }
            if (resource.getName().contains("--")) {
                diagnostics.add(Diagnostic.error("Container name cannot contain consecutive hyphens")
                        .withProperty("name"));
            }
        }

        if (resource.getStorageAccountName() == null || resource.getStorageAccountName().isBlank()) {
            diagnostics.add(Diagnostic.error("storageAccountName is required")
                    .withProperty("storageAccountName"));
        }

        if (resource.getResourceGroup() == null || resource.getResourceGroup().isBlank()) {
            diagnostics.add(Diagnostic.error("resourceGroup is required")
                    .withProperty("resourceGroup"));
        }

        if (resource.getPublicAccess() != null && !VALID_PUBLIC_ACCESS.contains(resource.getPublicAccess())) {
            diagnostics.add(Diagnostic.error("Invalid public access level",
                    "Valid values: " + String.join(", ", VALID_PUBLIC_ACCESS))
                    .withProperty("publicAccess"));
        }

        return diagnostics;
    }

    private BlobContainerResource mapContainerToResource(BlobContainer container,
                                                          String storageAccountName,
                                                          String resourceGroup) {
        var resource = new BlobContainerResource();

        // Input properties
        resource.setName(container.name());
        resource.setStorageAccountName(storageAccountName);
        resource.setResourceGroup(resourceGroup);

        if (container.publicAccess() != null) {
            resource.setPublicAccess(container.publicAccess().toString());
        }

        resource.setMetadata(container.metadata());

        // Cloud-managed properties
        resource.setId(container.id());
        resource.setEtag(container.etag());

        if (container.lastModifiedTime() != null) {
            resource.setLastModifiedTime(container.lastModifiedTime().toString());
        }

        if (container.leaseStatus() != null) {
            resource.setLeaseStatus(container.leaseStatus().toString());
        }

        if (container.leaseState() != null) {
            resource.setLeaseState(container.leaseState().toString());
        }

        resource.setHasImmutabilityPolicy(container.hasImmutabilityPolicy());
        resource.setHasLegalHold(container.hasLegalHold());

        return resource;
    }
}
