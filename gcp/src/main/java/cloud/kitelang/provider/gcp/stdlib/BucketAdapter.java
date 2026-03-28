package cloud.kitelang.provider.gcp.stdlib;

import cloud.kitelang.provider.ResourceTypeHandler;
import cloud.kitelang.provider.StandardTypeAdapter;
import cloud.kitelang.provider.gcp.storage.CloudStorageBucketResource;
import cloud.kitelang.provider.gcp.storage.CloudStorageBucketResourceType;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapts the standard library {@code Bucket} type to GCP {@code CloudStorageBucket}.
 *
 * <p>Property mapping:</p>
 * <ul>
 *   <li>{@code name} -> {@code name}</li>
 *   <li>{@code region} -> {@code location}</li>
 *   <li>{@code versioning} -> {@code versioning}</li>
 *   <li>{@code tags} -> {@code labels}</li>
 * </ul>
 *
 * <p>Cloud-managed properties mapped back:</p>
 * <ul>
 *   <li>{@code id} <- {@code name} (GCS bucket name serves as its ID)</li>
 *   <li>{@code arn} <- {@code selfLink} (GCP uses self-links instead of ARNs)</li>
 * </ul>
 */
public class BucketAdapter implements StandardTypeAdapter<CloudStorageBucketResource> {

    private final CloudStorageBucketResourceType handler;

    public BucketAdapter(CloudStorageBucketResourceType handler) {
        this.handler = handler;
    }

    @Override
    public String standardTypeName() {
        return "Bucket";
    }

    @Override
    public String concreteTypeName() {
        return "CloudStorageBucket";
    }

    @Override
    @SuppressWarnings("unchecked")
    public CloudStorageBucketResource toConcreteProperties(Map<String, Object> abstractProps) {
        var builder = CloudStorageBucketResource.builder();

        // name -> name
        var name = (String) abstractProps.get("name");
        if (name != null) {
            builder.name(name);
        }

        // region -> location
        var region = (String) abstractProps.get("region");
        if (region != null) {
            builder.location(region);
        }

        // versioning -> versioning
        var versioning = abstractProps.get("versioning");
        if (versioning instanceof Boolean value) {
            builder.versioning(value);
        }

        // tags -> labels
        var abstractTags = abstractProps.get("tags");
        if (abstractTags instanceof Map<?, ?> rawTags) {
            var labels = new HashMap<String, String>();
            rawTags.forEach((k, v) -> labels.put(String.valueOf(k), String.valueOf(v)));
            builder.labels(labels);
        }

        return builder.build();
    }

    @Override
    public Map<String, Object> toAbstractProperties(CloudStorageBucketResource concrete) {
        var result = new HashMap<String, Object>();

        // GCS bucket name serves as the abstract ID
        if (concrete.getName() != null) {
            result.put("id", concrete.getName());
        }
        // Map selfLink to arn (closest GCP equivalent)
        if (concrete.getSelfLink() != null) {
            result.put("arn", concrete.getSelfLink());
        }

        return result;
    }

    @Override
    public ResourceTypeHandler<CloudStorageBucketResource> getConcreteHandler() {
        return handler;
    }
}
