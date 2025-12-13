package cloud.kitelang.provider.azure;

import cloud.kitelang.provider.Diagnostic;
import cloud.kitelang.provider.ResourceTypeHandler;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.storage.StorageManager;
import com.azure.resourcemanager.storage.models.*;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ResourceTypeHandler for Azure Storage Account.
 * Implements CRUD operations using Azure Storage SDK.
 */
@Log4j2
public class StorageAccountResourceType extends ResourceTypeHandler<StorageAccountResource> {

    private static final Set<String> VALID_SKUS = Set.of(
            "Standard_LRS", "Standard_GRS", "Standard_RAGRS", "Standard_ZRS",
            "Premium_LRS", "Premium_ZRS"
    );

    private static final Set<String> VALID_KINDS = Set.of(
            "Storage", "StorageV2", "BlobStorage", "BlockBlobStorage", "FileStorage"
    );

    private static final Set<String> VALID_ACCESS_TIERS = Set.of("Hot", "Cool");

    private final StorageManager storageManager;

    public StorageAccountResourceType() {
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

    public StorageAccountResourceType(StorageManager storageManager) {
        this.storageManager = storageManager;
    }

    @Override
    public StorageAccountResource create(StorageAccountResource resource) {
        log.info("Creating Storage Account: {} in {}", resource.getName(), resource.getResourceGroup());

        var sku = resource.getSku() != null ? resource.getSku() : "Standard_LRS";
        var kind = resource.getKind() != null ? resource.getKind() : "StorageV2";

        var definition = storageManager.storageAccounts()
                .define(resource.getName())
                .withRegion(resource.getLocation())
                .withExistingResourceGroup(resource.getResourceGroup())
                .withSku(StorageAccountSkuType.fromSkuName(SkuName.fromString(sku)));

        // Set kind
        switch (kind) {
            case "BlobStorage" -> definition = definition.withBlobStorageAccountKind();
            case "BlockBlobStorage" -> definition = definition.withBlockBlobStorageAccountKind();
            case "FileStorage" -> definition = definition.withFileStorageAccountKind();
            default -> definition = definition.withGeneralPurposeAccountKindV2();
        }

        // Access tier (set after account kind for BlobStorage)
        // Note: Access tier is configured via update after creation for most scenarios

        // HTTPS only
        if (resource.getEnableHttpsTrafficOnly() != null && resource.getEnableHttpsTrafficOnly()) {
            definition = definition.withOnlyHttpsTraffic();
        }

        // Minimum TLS version
        if (resource.getMinimumTlsVersion() != null) {
            definition = definition.withMinimumTlsVersion(
                    MinimumTlsVersion.fromString(resource.getMinimumTlsVersion()));
        }

        // Blob public access
        if (resource.getAllowBlobPublicAccess() != null && !resource.getAllowBlobPublicAccess()) {
            definition = definition.disableBlobPublicAccess();
        }

        // Shared key access
        if (resource.getAllowSharedKeyAccess() != null && !resource.getAllowSharedKeyAccess()) {
            definition = definition.disableSharedKeyAccess();
        }

        // Hierarchical namespace (ADLS Gen2)
        if (resource.getEnableHierarchicalNamespace() != null && resource.getEnableHierarchicalNamespace()) {
            definition = definition.withHnsEnabled(true);
        }

        // Tags
        if (resource.getTags() != null) {
            definition = definition.withTags(resource.getTags());
        }

        var storageAccount = definition.create();

        // Configure network rules if specified
        if (resource.getNetworkRules() != null) {
            configureNetworkRules(storageAccount, resource.getNetworkRules());
        }

        log.info("Created Storage Account: {}", storageAccount.id());

        return read(resource);
    }

    @Override
    public StorageAccountResource read(StorageAccountResource resource) {
        if (resource.getName() == null || resource.getResourceGroup() == null) {
            log.warn("Cannot read Storage Account without name and resourceGroup");
            return null;
        }

        log.info("Reading Storage Account: {}", resource.getName());

        try {
            var storageAccount = storageManager.storageAccounts()
                    .getByResourceGroup(resource.getResourceGroup(), resource.getName());

            if (storageAccount == null) {
                return null;
            }

            return mapStorageAccountToResource(storageAccount);

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("ResourceNotFound")) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public StorageAccountResource update(StorageAccountResource resource) {
        log.info("Updating Storage Account: {}", resource.getName());

        var storageAccount = storageManager.storageAccounts()
                .getByResourceGroup(resource.getResourceGroup(), resource.getName());

        if (storageAccount == null) {
            throw new RuntimeException("Storage Account not found: " + resource.getName());
        }

        var update = storageAccount.update();

        // Update SKU
        if (resource.getSku() != null) {
            update = update.withSku(StorageAccountSkuType.fromSkuName(
                    SkuName.fromString(resource.getSku())));
        }

        // Update access tier
        if (resource.getAccessTier() != null) {
            update = update.withAccessTier(AccessTier.fromString(resource.getAccessTier()));
        }

        // Update tags
        if (resource.getTags() != null) {
            update = update.withTags(resource.getTags());
        }

        update.apply();

        // Update network rules
        if (resource.getNetworkRules() != null) {
            configureNetworkRules(storageAccount, resource.getNetworkRules());
        }

        return read(resource);
    }

    @Override
    public boolean delete(StorageAccountResource resource) {
        if (resource.getName() == null || resource.getResourceGroup() == null) {
            log.warn("Cannot delete Storage Account without name and resourceGroup");
            return false;
        }

        log.info("Deleting Storage Account: {}", resource.getName());

        try {
            storageManager.storageAccounts()
                    .deleteByResourceGroup(resource.getResourceGroup(), resource.getName());

            log.info("Deleted Storage Account: {}", resource.getName());
            return true;

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("ResourceNotFound")) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public List<Diagnostic> validate(StorageAccountResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getName() == null || resource.getName().isBlank()) {
            diagnostics.add(Diagnostic.error("name is required")
                    .withProperty("name"));
        } else {
            // Validate storage account name format
            if (!resource.getName().matches("^[a-z0-9]{3,24}$")) {
                diagnostics.add(Diagnostic.error("Invalid storage account name",
                        "Name must be 3-24 characters, lowercase letters and numbers only")
                        .withProperty("name"));
            }
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
            diagnostics.add(Diagnostic.error("Invalid SKU",
                    "Valid values: " + String.join(", ", VALID_SKUS))
                    .withProperty("sku"));
        }

        if (resource.getKind() != null && !VALID_KINDS.contains(resource.getKind())) {
            diagnostics.add(Diagnostic.error("Invalid kind",
                    "Valid values: " + String.join(", ", VALID_KINDS))
                    .withProperty("kind"));
        }

        if (resource.getAccessTier() != null && !VALID_ACCESS_TIERS.contains(resource.getAccessTier())) {
            diagnostics.add(Diagnostic.error("Invalid access tier",
                    "Valid values: " + String.join(", ", VALID_ACCESS_TIERS))
                    .withProperty("accessTier"));
        }

        return diagnostics;
    }

    private void configureNetworkRules(StorageAccount storageAccount, StorageAccountResource.NetworkRules rules) {
        var update = storageAccount.update();

        if ("Deny".equalsIgnoreCase(rules.getDefaultAction())) {
            update = update.withAccessFromSelectedNetworks();

            // Add IP rules
            if (rules.getIpRules() != null) {
                for (var ip : rules.getIpRules()) {
                    update = update.withAccessFromIpAddress(ip);
                }
            }

            // Add virtual network rules
            if (rules.getVirtualNetworkSubnetIds() != null) {
                for (var subnetId : rules.getVirtualNetworkSubnetIds()) {
                    update = update.withAccessFromNetworkSubnet(subnetId);
                }
            }

            // Configure bypass
            if (rules.getBypass() != null && rules.getBypass().contains("AzureServices")) {
                update = update.withAccessFromAzureServices();
            }
        } else {
            update = update.withAccessFromAllNetworks();
        }

        update.apply();
    }

    private StorageAccountResource mapStorageAccountToResource(StorageAccount storageAccount) {
        var resource = new StorageAccountResource();

        // Input properties
        resource.setName(storageAccount.name());
        resource.setResourceGroup(storageAccount.resourceGroupName());
        resource.setLocation(storageAccount.regionName());
        resource.setSku(storageAccount.skuType().name().toString());
        resource.setKind(storageAccount.kind().toString());

        if (storageAccount.accessTier() != null) {
            resource.setAccessTier(storageAccount.accessTier().toString());
        }

        resource.setEnableHttpsTrafficOnly(storageAccount.innerModel().enableHttpsTrafficOnly());
        resource.setMinimumTlsVersion(storageAccount.minimumTlsVersion() != null
                ? storageAccount.minimumTlsVersion().toString() : null);
        resource.setAllowBlobPublicAccess(storageAccount.innerModel().allowBlobPublicAccess());
        resource.setAllowSharedKeyAccess(storageAccount.innerModel().allowSharedKeyAccess());
        resource.setEnableHierarchicalNamespace(storageAccount.isHnsEnabled());
        resource.setTags(storageAccount.tags());

        // Cloud-managed properties
        resource.setId(storageAccount.id());
        resource.setProvisioningState(storageAccount.provisioningState().toString());

        var endpoints = storageAccount.endPoints();
        if (endpoints != null && endpoints.primary() != null) {
            resource.setPrimaryBlobEndpoint(endpoints.primary().blob());
            resource.setPrimaryFileEndpoint(endpoints.primary().file());
            resource.setPrimaryQueueEndpoint(endpoints.primary().queue());
            resource.setPrimaryTableEndpoint(endpoints.primary().table());
        }

        // Get access keys
        try {
            var keys = storageAccount.getKeys();
            if (keys != null && !keys.isEmpty()) {
                var primaryKey = keys.get(0).value();
                resource.setPrimaryAccessKey(primaryKey);
                resource.setPrimaryConnectionString(String.format(
                        "DefaultEndpointsProtocol=https;AccountName=%s;AccountKey=%s;EndpointSuffix=core.windows.net",
                        storageAccount.name(), primaryKey));
            }
        } catch (Exception e) {
            log.debug("Could not retrieve storage account keys: {}", e.getMessage());
        }

        return resource;
    }
}
