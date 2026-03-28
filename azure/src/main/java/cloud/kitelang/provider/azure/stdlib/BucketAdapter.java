package cloud.kitelang.provider.azure.stdlib;

import cloud.kitelang.provider.ResourceTypeHandler;
import cloud.kitelang.provider.StandardTypeAdapter;
import cloud.kitelang.provider.azure.storage.StorageAccountResource;
import cloud.kitelang.provider.azure.storage.StorageAccountResourceType;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapts the standard library {@code Bucket} type to Azure {@code StorageAccount}.
 *
 * <p>Azure Storage Accounts are the closest equivalent to S3-style buckets.
 * A storage account provides the namespace for blob containers, file shares,
 * queues, and tables -- making it the natural mapping target for the abstract Bucket type.</p>
 *
 * <p>Property mapping:</p>
 * <ul>
 *   <li>{@code name} -> {@code name} (must be 3-24 chars, lowercase, globally unique)</li>
 *   <li>{@code region} -> {@code location}</li>
 *   <li>{@code versioning} -> {@code enableHierarchicalNamespace} (closest analog; blob versioning
 *       is a more granular setting not exposed as a top-level property)</li>
 *   <li>{@code tags} -> {@code tags}</li>
 * </ul>
 *
 * <p>Cloud-managed properties mapped back:</p>
 * <ul>
 *   <li>{@code id} <- {@code id}</li>
 *   <li>{@code arn} <- {@code primaryBlobEndpoint} (Azure does not have ARNs;
 *       the blob endpoint serves as the unique resource URL)</li>
 * </ul>
 */
public class BucketAdapter implements StandardTypeAdapter<StorageAccountResource> {

    private final StorageAccountResourceType handler;

    public BucketAdapter(StorageAccountResourceType handler) {
        this.handler = handler;
    }

    @Override
    public String standardTypeName() {
        return "Bucket";
    }

    @Override
    public String concreteTypeName() {
        return "StorageAccount";
    }

    @Override
    @SuppressWarnings("unchecked")
    public StorageAccountResource toConcreteProperties(Map<String, Object> abstractProps) {
        var builder = StorageAccountResource.builder();

        // Map name -> name
        var name = (String) abstractProps.get("name");
        if (name != null) {
            builder.name(name);
        }

        // Map region -> location
        var region = (String) abstractProps.get("region");
        if (region != null) {
            builder.location(region);
        }

        // Map versioning -> blob versioning hint
        // Azure blob versioning is enabled separately; for the adapter, we use
        // the enableHierarchicalNamespace flag as a "data lake" style hint.
        // StorageV2 with Standard_LRS is a sensible default for bucket-like storage.
        var versioning = abstractProps.get("versioning");
        if (versioning instanceof Boolean value) {
            builder.enableHierarchicalNamespace(value);
        }

        // Map tags directly
        var abstractTags = abstractProps.get("tags");
        if (abstractTags instanceof Map<?, ?> rawTags) {
            var tags = new HashMap<String, String>();
            rawTags.forEach((k, v) -> tags.put(String.valueOf(k), String.valueOf(v)));
            builder.tags(tags);
        }

        return builder.build();
    }

    @Override
    public Map<String, Object> toAbstractProperties(StorageAccountResource concrete) {
        var result = new HashMap<String, Object>();

        if (concrete.getId() != null) {
            result.put("id", concrete.getId());
        }
        // Azure does not have ARNs; use the blob endpoint as the unique resource URL
        if (concrete.getPrimaryBlobEndpoint() != null) {
            result.put("arn", concrete.getPrimaryBlobEndpoint());
        }

        return result;
    }

    @Override
    public ResourceTypeHandler<StorageAccountResource> getConcreteHandler() {
        return handler;
    }
}
