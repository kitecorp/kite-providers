package cloud.kitelang.provider.azure;

import cloud.kitelang.provider.Diagnostic;
import cloud.kitelang.provider.ResourceTypeHandler;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.Region;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.resources.ResourceManager;
import com.azure.resourcemanager.resources.models.ResourceGroup;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * ResourceTypeHandler for Azure Resource Group.
 * Implements CRUD operations for Resource Groups using Azure SDK.
 */
@Log4j2
public class ResourceGroupResourceType extends ResourceTypeHandler<ResourceGroupResource> {

    private final ResourceManager resourceManager;

    public ResourceGroupResourceType() {
        var credential = new DefaultAzureCredentialBuilder().build();

        String subscriptionId = System.getenv("AZURE_SUBSCRIPTION_ID");
        if (subscriptionId == null || subscriptionId.isBlank()) {
            throw new IllegalStateException(
                    "AZURE_SUBSCRIPTION_ID environment variable must be set");
        }

        String tenantId = System.getenv("AZURE_TENANT_ID");
        var profile = new AzureProfile(tenantId, subscriptionId, AzureEnvironment.AZURE);

        this.resourceManager = ResourceManager.authenticate(credential, profile)
                .withSubscription(subscriptionId);
    }

    public ResourceGroupResourceType(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    @Override
    public ResourceGroupResource create(ResourceGroupResource resource) {
        log.info("Creating Resource Group '{}' at '{}'",
                resource.getName(), resource.getLocation());

        var definition = resourceManager.resourceGroups()
                .define(resource.getName())
                .withRegion(Region.fromName(resource.getLocation()));

        // Add tags if specified
        if (resource.getTags() != null && !resource.getTags().isEmpty()) {
            definition = definition.withTags(resource.getTags());
        }

        ResourceGroup rg = definition.create();
        log.info("Created Resource Group: {}", rg.id());

        return mapToResource(rg);
    }

    @Override
    public ResourceGroupResource read(ResourceGroupResource resource) {
        if (resource.getName() == null) {
            log.warn("Cannot read Resource Group without name");
            return null;
        }

        log.info("Reading Resource Group '{}'", resource.getName());

        try {
            ResourceGroup rg = resourceManager.resourceGroups().getByName(resource.getName());

            if (rg == null) {
                return null;
            }

            return mapToResource(rg);

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("ResourceGroupNotFound")) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public ResourceGroupResource update(ResourceGroupResource resource) {
        log.info("Updating Resource Group '{}'", resource.getName());

        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("Resource Group not found: " + resource.getName());
        }

        ResourceGroup rg = resourceManager.resourceGroups().getByName(resource.getName());

        var update = rg.update();

        // Update tags
        if (resource.getTags() != null) {
            update = update.withTags(resource.getTags());
        }

        rg = update.apply();
        return mapToResource(rg);
    }

    @Override
    public boolean delete(ResourceGroupResource resource) {
        if (resource.getName() == null) {
            log.warn("Cannot delete Resource Group without name");
            return false;
        }

        log.info("Deleting Resource Group '{}'", resource.getName());

        try {
            resourceManager.resourceGroups().deleteByName(resource.getName());
            log.info("Deleted Resource Group: {}", resource.getName());
            return true;

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("ResourceGroupNotFound")) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public List<Diagnostic> validate(ResourceGroupResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getName() == null || resource.getName().isBlank()) {
            diagnostics.add(Diagnostic.error("name is required")
                    .withProperty("name"));
        } else {
            // Azure Resource Group name validation
            if (resource.getName().length() < 1 || resource.getName().length() > 90) {
                diagnostics.add(Diagnostic.error("name must be between 1 and 90 characters")
                        .withProperty("name"));
            }
            // Resource group names can only include alphanumeric, underscore, parentheses, hyphen, period
            // Cannot end with period
            if (!resource.getName().matches("^[a-zA-Z0-9._()-]+$")) {
                diagnostics.add(Diagnostic.error("name can only contain alphanumeric, " +
                        "underscores, parentheses, hyphens, and periods")
                        .withProperty("name"));
            }
            if (resource.getName().endsWith(".")) {
                diagnostics.add(Diagnostic.error("name cannot end with a period")
                        .withProperty("name"));
            }
        }

        if (resource.getLocation() == null || resource.getLocation().isBlank()) {
            diagnostics.add(Diagnostic.error("location is required")
                    .withProperty("location"));
        }

        return diagnostics;
    }

    private ResourceGroupResource mapToResource(ResourceGroup rg) {
        var resource = new ResourceGroupResource();

        resource.setName(rg.name());
        resource.setLocation(rg.regionName());

        if (rg.tags() != null && !rg.tags().isEmpty()) {
            resource.setTags(new HashMap<>(rg.tags()));
        }

        // Cloud-managed properties
        resource.setId(rg.id());
        resource.setProvisioningState(rg.provisioningState());

        return resource;
    }
}
