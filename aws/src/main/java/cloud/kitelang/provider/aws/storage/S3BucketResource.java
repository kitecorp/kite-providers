package cloud.kitelang.provider.aws.storage;

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

    @Property(description = "Bucket name (globally unique, DNS-compliant)", optional = false)
    private String bucket;

    @Property(description = "Canned ACL for the bucket",
              validValues = {"private", "public-read", "public-read-write", "authenticated-read"})
    private String acl = "private";

    @Property(description = "Enable versioning for the bucket")
    private Boolean versioning = false;

    @Property(description = "AWS region for the bucket")
    private String region;

    @Property(description = "Server-side encryption algorithm",
              validValues = {"AES256", "aws:kms"})
    private String serverSideEncryption;

    @Property(description = "KMS key ID for encryption (when using aws:kms)")
    private String kmsKeyId;

    @Property(description = "Block all public access")
    private Boolean blockPublicAccess = true;

    @Property(description = "Index document for static website hosting")
    private String websiteIndexDocument;

    @Property(description = "Error document for static website hosting")
    private String websiteErrorDocument;

    @Property(description = "Redirect all requests to another host")
    private String websiteRedirectAllRequestsTo;

    @Property(description = "CORS configuration rules")
    private List<CorsRule> corsRules;

    @Property(description = "Lifecycle rules for object management")
    private List<LifecycleRule> lifecycleRules;

    @Property(description = "Access logging configuration")
    private LoggingConfig logging;

    @Property(description = "Tags to apply to the bucket")
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    @Cloud(importable = true)
    @Property(description = "The ARN of the bucket")
    private String arn;

    @Cloud
    @Property(description = "The domain name of the bucket")
    private String domainName;

    @Cloud
    @Property(description = "The regional domain name of the bucket")
    private String regionalDomainName;

    @Cloud
    @Property(description = "Website endpoint (if hosting enabled)")
    private String websiteEndpoint;

    @Cloud
    @Property(description = "The creation date of the bucket")
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
