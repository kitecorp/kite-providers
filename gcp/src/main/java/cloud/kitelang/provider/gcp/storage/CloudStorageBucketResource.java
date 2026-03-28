package cloud.kitelang.provider.gcp.storage;

import cloud.kitelang.api.annotations.Cloud;
import cloud.kitelang.api.annotations.Property;
import cloud.kitelang.api.annotations.TypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * GCP Cloud Storage Bucket - object storage in the cloud.
 *
 * Example usage:
 * <pre>
 * resource CloudStorageBucket assets {
 *     name = "my-project-assets"
 *     location = "US"
 *     storageClass = "STANDARD"
 *     versioning = true
 *     labels = {
 *         environment: "production"
 *     }
 * }
 * </pre>
 *
 * @see <a href="https://cloud.google.com/storage/docs">GCP Cloud Storage Documentation</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("CloudStorageBucket")
public class CloudStorageBucketResource {

    @Property(description = "Globally unique bucket name", optional = false)
    private String name;

    @Property(description = "The location of the bucket (e.g., US, EU, us-central1)")
    private String location = "US";

    @Property(description = "The storage class for the bucket",
              validValues = {"STANDARD", "NEARLINE", "COLDLINE", "ARCHIVE"})
    private String storageClass = "STANDARD";

    @Property(description = "Enable object versioning")
    private Boolean versioning = false;

    @Property(description = "Labels to apply to the bucket")
    private Map<String, String> labels;

    @Property(description = "Whether uniform bucket-level access is enabled")
    private Boolean uniformBucketLevelAccess = true;

    // --- Cloud-managed properties (read-only) ---

    @Cloud(importable = true)
    @Property(description = "The self-link URL of the bucket")
    private String selfLink;

    @Cloud
    @Property(description = "The creation time of the bucket")
    private String timeCreated;
}
