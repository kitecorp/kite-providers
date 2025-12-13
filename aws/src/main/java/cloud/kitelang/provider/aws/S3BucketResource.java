package cloud.kitelang.provider.aws;

import cloud.kitelang.api.annotations.Cloud;
import cloud.kitelang.api.annotations.Property;
import cloud.kitelang.api.annotations.TypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * AWS S3 Bucket resource.
 *
 * Example usage:
 * <pre>
 * resource S3Bucket website {
 *     bucket = "my-website-bucket"
 *     acl = "private"
 *     versioning = true
 *     tags = {
 *         Environment: "production"
 *     }
 * }
 *
 * resource S3Bucket logs {
 *     bucket = "my-logs-bucket"
 *     lifecycleRules = [
 *         {
 *             id: "expire-old-logs",
 *             enabled: true,
 *             prefix: "logs/",
 *             expirationDays: 90
 *         }
 *     ]
 * }
 *
 * resource S3Bucket static {
 *     bucket = "my-static-content"
 *     websiteIndexDocument = "index.html"
 *     websiteErrorDocument = "error.html"
 *     corsRules = [
 *         {
 *             allowedOrigins: ["*"],
 *             allowedMethods: ["GET"],
 *             allowedHeaders: ["*"],
 *             maxAgeSeconds: 3000
 *         }
 *     ]
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("S3Bucket")
public class S3BucketResource {

    /**
     * The name of the bucket.
     * Must be globally unique and DNS-compliant.
     * Required.
     */
    @Property
    private String bucket;

    /**
     * The canned ACL to apply.
     * Valid values: private, public-read, public-read-write, authenticated-read.
     * Default: private
     */
    @Property
    private String acl;

    /**
     * Enable versioning for the bucket.
     */
    @Property
    private Boolean versioning;

    /**
     * The AWS region for the bucket.
     * If not specified, uses the default region.
     */
    @Property
    private String region;

    /**
     * Enable server-side encryption by default.
     * Valid values: AES256, aws:kms
     */
    @Property
    private String serverSideEncryption;

    /**
     * KMS key ID for encryption (when serverSideEncryption is aws:kms).
     */
    @Property
    private String kmsKeyId;

    /**
     * Block all public access to the bucket.
     * Default: true
     */
    @Property
    private Boolean blockPublicAccess;

    /**
     * Index document for static website hosting.
     */
    @Property
    private String websiteIndexDocument;

    /**
     * Error document for static website hosting.
     */
    @Property
    private String websiteErrorDocument;

    /**
     * Redirect all requests to another host.
     */
    @Property
    private String websiteRedirectAllRequestsTo;

    /**
     * CORS configuration rules.
     */
    @Property
    private List<CorsRule> corsRules;

    /**
     * Lifecycle rules for object management.
     */
    @Property
    private List<LifecycleRule> lifecycleRules;

    /**
     * Logging configuration.
     */
    @Property
    private LoggingConfig logging;

    /**
     * Tags to apply to the bucket.
     */
    @Property
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    /**
     * The ARN of the bucket.
     */
    @Cloud
    @Property
    private String arn;

    /**
     * The domain name of the bucket.
     */
    @Cloud
    @Property
    private String domainName;

    /**
     * The regional domain name of the bucket.
     */
    @Cloud
    @Property
    private String regionalDomainName;

    /**
     * The website endpoint (if website hosting enabled).
     */
    @Cloud
    @Property
    private String websiteEndpoint;

    /**
     * The creation date of the bucket.
     */
    @Cloud
    @Property
    private String creationDate;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CorsRule {
        /**
         * Allowed origins (e.g., ["https://example.com"] or ["*"]).
         */
        private List<String> allowedOrigins;

        /**
         * Allowed HTTP methods (GET, PUT, POST, DELETE, HEAD).
         */
        private List<String> allowedMethods;

        /**
         * Allowed headers.
         */
        private List<String> allowedHeaders;

        /**
         * Headers to expose in the response.
         */
        private List<String> exposeHeaders;

        /**
         * Max age in seconds for preflight cache.
         */
        private Integer maxAgeSeconds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LifecycleRule {
        /**
         * Unique identifier for the rule.
         */
        private String id;

        /**
         * Whether the rule is enabled.
         */
        private Boolean enabled;

        /**
         * Object key prefix to apply the rule to.
         */
        private String prefix;

        /**
         * Days after which to expire objects.
         */
        private Integer expirationDays;

        /**
         * Days after which to transition to a storage class.
         */
        private Integer transitionDays;

        /**
         * Storage class to transition to (GLACIER, DEEP_ARCHIVE, INTELLIGENT_TIERING, etc.).
         */
        private String transitionStorageClass;

        /**
         * Days after which to expire incomplete multipart uploads.
         */
        private Integer abortIncompleteMultipartUploadDays;

        /**
         * Days after which to expire noncurrent versions.
         */
        private Integer noncurrentVersionExpirationDays;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoggingConfig {
        /**
         * Target bucket for access logs.
         */
        private String targetBucket;

        /**
         * Prefix for log objects.
         */
        private String targetPrefix;
    }
}
