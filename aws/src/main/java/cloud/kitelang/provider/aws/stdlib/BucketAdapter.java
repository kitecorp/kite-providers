package cloud.kitelang.provider.aws.stdlib;

import cloud.kitelang.provider.ResourceTypeHandler;
import cloud.kitelang.provider.StandardTypeAdapter;
import cloud.kitelang.provider.aws.storage.S3BucketResource;
import cloud.kitelang.provider.aws.storage.S3BucketResourceType;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapts the standard library {@code Bucket} type to AWS {@code S3Bucket}.
 *
 * <p>Property mapping:</p>
 * <ul>
 *   <li>{@code name} -> {@code bucket}</li>
 *   <li>{@code region} -> {@code region}</li>
 *   <li>{@code versioning} -> {@code versioning}</li>
 *   <li>{@code tags} -> {@code tags}</li>
 * </ul>
 *
 * <p>Cloud-managed properties mapped back:</p>
 * <ul>
 *   <li>{@code id} <- {@code bucket} (S3 bucket name serves as its ID)</li>
 *   <li>{@code arn} <- {@code arn}</li>
 * </ul>
 */
public class BucketAdapter implements StandardTypeAdapter<S3BucketResource> {

    private final S3BucketResourceType handler;

    public BucketAdapter(S3BucketResourceType handler) {
        this.handler = handler;
    }

    @Override
    public String standardTypeName() {
        return "Bucket";
    }

    @Override
    public String concreteTypeName() {
        return "S3Bucket";
    }

    @Override
    @SuppressWarnings("unchecked")
    public S3BucketResource toConcreteProperties(Map<String, Object> abstractProps) {
        var builder = S3BucketResource.builder();

        // name -> bucket
        var name = (String) abstractProps.get("name");
        if (name != null) {
            builder.bucket(name);
        }

        var region = (String) abstractProps.get("region");
        if (region != null) {
            builder.region(region);
        }

        var versioning = abstractProps.get("versioning");
        if (versioning instanceof Boolean value) {
            builder.versioning(value);
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
    public Map<String, Object> toAbstractProperties(S3BucketResource concrete) {
        var result = new HashMap<String, Object>();

        // S3 bucket name serves as the abstract ID
        if (concrete.getBucket() != null) {
            result.put("id", concrete.getBucket());
        }
        if (concrete.getArn() != null) {
            result.put("arn", concrete.getArn());
        }

        return result;
    }

    @Override
    public ResourceTypeHandler<S3BucketResource> getConcreteHandler() {
        return handler;
    }
}
