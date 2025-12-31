package cloud.kitelang.provider.aws.storage;

import cloud.kitelang.provider.Diagnostic;
import cloud.kitelang.provider.ResourceTypeHandler;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ResourceTypeHandler for AWS S3 Bucket.
 * Implements CRUD operations using AWS S3 SDK.
 */
@Slf4j
public class S3BucketResourceType extends ResourceTypeHandler<S3BucketResource> {

    private static final Set<String> VALID_ACLS = Set.of(
            "private", "public-read", "public-read-write", "authenticated-read"
    );

    private static final Set<String> VALID_SSE = Set.of("AES256", "aws:kms");

    private final S3Client s3Client;
    private final String defaultRegion;

    public S3BucketResourceType() {
        this.defaultRegion = System.getenv("AWS_REGION") != null
                ? System.getenv("AWS_REGION")
                : "us-east-1";
        this.s3Client = S3Client.builder()
                .region(Region.of(defaultRegion))
                .build();
    }

    public S3BucketResourceType(S3Client s3Client, String defaultRegion) {
        this.s3Client = s3Client;
        this.defaultRegion = defaultRegion;
    }

    @Override
    public S3BucketResource create(S3BucketResource resource) {
        log.info("Creating S3 Bucket: {}", resource.getBucket());

        var createRequest = CreateBucketRequest.builder()
                .bucket(resource.getBucket());

        // Set region (only needed for non-us-east-1)
        var region = resource.getRegion() != null ? resource.getRegion() : defaultRegion;
        if (!"us-east-1".equals(region)) {
            createRequest.createBucketConfiguration(
                    CreateBucketConfiguration.builder()
                            .locationConstraint(BucketLocationConstraint.fromValue(region))
                            .build());
        }

        // Set ACL if specified
        if (resource.getAcl() != null) {
            createRequest.acl(BucketCannedACL.fromValue(resource.getAcl().replace("-", "_").toUpperCase()));
        }

        s3Client.createBucket(createRequest.build());
        log.info("Created S3 Bucket: {}", resource.getBucket());

        // Configure bucket settings
        configureBucket(resource);

        return read(resource);
    }

    @Override
    public S3BucketResource read(S3BucketResource resource) {
        if (resource.getBucket() == null) {
            log.warn("Cannot read S3 Bucket without bucket name");
            return null;
        }

        log.info("Reading S3 Bucket: {}", resource.getBucket());

        try {
            // Check if bucket exists
            s3Client.headBucket(HeadBucketRequest.builder()
                    .bucket(resource.getBucket())
                    .build());

            return mapBucketToResource(resource.getBucket());

        } catch (NoSuchBucketException e) {
            return null;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public S3BucketResource update(S3BucketResource resource) {
        log.info("Updating S3 Bucket: {}", resource.getBucket());

        // Bucket name cannot be changed, only configuration
        configureBucket(resource);

        return read(resource);
    }

    @Override
    public boolean delete(S3BucketResource resource) {
        if (resource.getBucket() == null) {
            log.warn("Cannot delete S3 Bucket without bucket name");
            return false;
        }

        log.info("Deleting S3 Bucket: {}", resource.getBucket());

        try {
            // First, delete all objects (bucket must be empty to delete)
            deleteAllObjects(resource.getBucket());

            // Delete the bucket
            s3Client.deleteBucket(DeleteBucketRequest.builder()
                    .bucket(resource.getBucket())
                    .build());

            log.info("Deleted S3 Bucket: {}", resource.getBucket());
            return true;

        } catch (NoSuchBucketException e) {
            return false;
        }
    }

    @Override
    public List<Diagnostic> validate(S3BucketResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getBucket() == null || resource.getBucket().isBlank()) {
            diagnostics.add(Diagnostic.error("bucket is required")
                    .withProperty("bucket"));
        } else {
            // Validate bucket name format
            if (!resource.getBucket().matches("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$")) {
                diagnostics.add(Diagnostic.error("Invalid bucket name format",
                        "Bucket names must be 3-63 characters, lowercase, and DNS-compliant")
                        .withProperty("bucket"));
            }
            if (resource.getBucket().contains("..")) {
                diagnostics.add(Diagnostic.error("Bucket name cannot contain consecutive periods")
                        .withProperty("bucket"));
            }
        }

        if (resource.getAcl() != null && !VALID_ACLS.contains(resource.getAcl())) {
            diagnostics.add(Diagnostic.error("Invalid ACL",
                    "Valid values: " + String.join(", ", VALID_ACLS))
                    .withProperty("acl"));
        }

        if (resource.getServerSideEncryption() != null &&
            !VALID_SSE.contains(resource.getServerSideEncryption())) {
            diagnostics.add(Diagnostic.error("Invalid server-side encryption",
                    "Valid values: " + String.join(", ", VALID_SSE))
                    .withProperty("serverSideEncryption"));
        }

        if ("aws:kms".equals(resource.getServerSideEncryption()) && resource.getKmsKeyId() == null) {
            diagnostics.add(Diagnostic.error("kmsKeyId is required when using aws:kms encryption")
                    .withProperty("kmsKeyId"));
        }

        return diagnostics;
    }

    private void configureBucket(S3BucketResource resource) {
        // Configure versioning
        if (resource.getVersioning() != null) {
            var versioningStatus = resource.getVersioning()
                    ? BucketVersioningStatus.ENABLED
                    : BucketVersioningStatus.SUSPENDED;

            s3Client.putBucketVersioning(PutBucketVersioningRequest.builder()
                    .bucket(resource.getBucket())
                    .versioningConfiguration(VersioningConfiguration.builder()
                            .status(versioningStatus)
                            .build())
                    .build());
        }

        // Configure encryption
        if (resource.getServerSideEncryption() != null) {
            var sseBuilder = ServerSideEncryptionByDefault.builder();

            if ("AES256".equals(resource.getServerSideEncryption())) {
                sseBuilder.sseAlgorithm(ServerSideEncryption.AES256);
            } else if ("aws:kms".equals(resource.getServerSideEncryption())) {
                sseBuilder.sseAlgorithm(ServerSideEncryption.AWS_KMS)
                        .kmsMasterKeyID(resource.getKmsKeyId());
            }

            s3Client.putBucketEncryption(PutBucketEncryptionRequest.builder()
                    .bucket(resource.getBucket())
                    .serverSideEncryptionConfiguration(ServerSideEncryptionConfiguration.builder()
                            .rules(ServerSideEncryptionRule.builder()
                                    .applyServerSideEncryptionByDefault(sseBuilder.build())
                                    .build())
                            .build())
                    .build());
        }

        // Configure public access block
        if (resource.getBlockPublicAccess() != null && resource.getBlockPublicAccess()) {
            s3Client.putPublicAccessBlock(PutPublicAccessBlockRequest.builder()
                    .bucket(resource.getBucket())
                    .publicAccessBlockConfiguration(PublicAccessBlockConfiguration.builder()
                            .blockPublicAcls(true)
                            .ignorePublicAcls(true)
                            .blockPublicPolicy(true)
                            .restrictPublicBuckets(true)
                            .build())
                    .build());
        }

        // Configure website hosting
        if (resource.getWebsiteIndexDocument() != null || resource.getWebsiteRedirectAllRequestsTo() != null) {
            var websiteBuilder = WebsiteConfiguration.builder();

            if (resource.getWebsiteRedirectAllRequestsTo() != null) {
                websiteBuilder.redirectAllRequestsTo(RedirectAllRequestsTo.builder()
                        .hostName(resource.getWebsiteRedirectAllRequestsTo())
                        .build());
            } else {
                websiteBuilder.indexDocument(IndexDocument.builder()
                        .suffix(resource.getWebsiteIndexDocument())
                        .build());

                if (resource.getWebsiteErrorDocument() != null) {
                    websiteBuilder.errorDocument(ErrorDocument.builder()
                            .key(resource.getWebsiteErrorDocument())
                            .build());
                }
            }

            s3Client.putBucketWebsite(PutBucketWebsiteRequest.builder()
                    .bucket(resource.getBucket())
                    .websiteConfiguration(websiteBuilder.build())
                    .build());
        }

        // Configure CORS
        if (resource.getCorsRules() != null && !resource.getCorsRules().isEmpty()) {
            var corsRules = resource.getCorsRules().stream()
                    .map(rule -> CORSRule.builder()
                            .allowedOrigins(rule.getAllowedOrigins())
                            .allowedMethods(rule.getAllowedMethods())
                            .allowedHeaders(rule.getAllowedHeaders())
                            .exposeHeaders(rule.getExposeHeaders())
                            .maxAgeSeconds(rule.getMaxAgeSeconds())
                            .build())
                    .collect(Collectors.toList());

            s3Client.putBucketCors(PutBucketCorsRequest.builder()
                    .bucket(resource.getBucket())
                    .corsConfiguration(CORSConfiguration.builder()
                            .corsRules(corsRules)
                            .build())
                    .build());
        }

        // Configure lifecycle rules
        if (resource.getLifecycleRules() != null && !resource.getLifecycleRules().isEmpty()) {
            var lifecycleRules = resource.getLifecycleRules().stream()
                    .map(this::mapLifecycleRule)
                    .collect(Collectors.toList());

            s3Client.putBucketLifecycleConfiguration(PutBucketLifecycleConfigurationRequest.builder()
                    .bucket(resource.getBucket())
                    .lifecycleConfiguration(BucketLifecycleConfiguration.builder()
                            .rules(lifecycleRules)
                            .build())
                    .build());
        }

        // Configure logging
        if (resource.getLogging() != null) {
            s3Client.putBucketLogging(PutBucketLoggingRequest.builder()
                    .bucket(resource.getBucket())
                    .bucketLoggingStatus(BucketLoggingStatus.builder()
                            .loggingEnabled(LoggingEnabled.builder()
                                    .targetBucket(resource.getLogging().getTargetBucket())
                                    .targetPrefix(resource.getLogging().getTargetPrefix())
                                    .build())
                            .build())
                    .build());
        }

        // Configure tags
        if (resource.getTags() != null && !resource.getTags().isEmpty()) {
            var tags = resource.getTags().entrySet().stream()
                    .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                    .collect(Collectors.toList());

            s3Client.putBucketTagging(PutBucketTaggingRequest.builder()
                    .bucket(resource.getBucket())
                    .tagging(Tagging.builder().tagSet(tags).build())
                    .build());
        }
    }

    private LifecycleRule mapLifecycleRule(S3BucketResource.LifecycleRule rule) {
        var builder = LifecycleRule.builder()
                .id(rule.getId())
                .status(rule.getEnabled() ? ExpirationStatus.ENABLED : ExpirationStatus.DISABLED);

        // Set filter (prefix-based or apply to all)
        if (rule.getPrefix() != null && !rule.getPrefix().isEmpty()) {
            builder.filter(LifecycleRuleFilter.builder()
                    .prefix(rule.getPrefix())
                    .build());
        } else {
            builder.filter(LifecycleRuleFilter.builder().prefix("").build());
        }

        // Expiration
        if (rule.getExpirationDays() != null) {
            builder.expiration(LifecycleExpiration.builder()
                    .days(rule.getExpirationDays())
                    .build());
        }

        // Transitions
        if (rule.getTransitionDays() != null && rule.getTransitionStorageClass() != null) {
            builder.transitions(Transition.builder()
                    .days(rule.getTransitionDays())
                    .storageClass(TransitionStorageClass.fromValue(rule.getTransitionStorageClass()))
                    .build());
        }

        // Abort incomplete multipart uploads
        if (rule.getAbortIncompleteMultipartUploadDays() != null) {
            builder.abortIncompleteMultipartUpload(AbortIncompleteMultipartUpload.builder()
                    .daysAfterInitiation(rule.getAbortIncompleteMultipartUploadDays())
                    .build());
        }

        // Noncurrent version expiration
        if (rule.getNoncurrentVersionExpirationDays() != null) {
            builder.noncurrentVersionExpiration(NoncurrentVersionExpiration.builder()
                    .noncurrentDays(rule.getNoncurrentVersionExpirationDays())
                    .build());
        }

        return builder.build();
    }

    private void deleteAllObjects(String bucket) {
        // List and delete all objects
        var listRequest = ListObjectsV2Request.builder().bucket(bucket).build();
        ListObjectsV2Response listResponse;

        do {
            listResponse = s3Client.listObjectsV2(listRequest);

            if (!listResponse.contents().isEmpty()) {
                var objectsToDelete = listResponse.contents().stream()
                        .map(obj -> ObjectIdentifier.builder().key(obj.key()).build())
                        .collect(Collectors.toList());

                s3Client.deleteObjects(DeleteObjectsRequest.builder()
                        .bucket(bucket)
                        .delete(Delete.builder().objects(objectsToDelete).build())
                        .build());
            }

            listRequest = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .continuationToken(listResponse.nextContinuationToken())
                    .build();

        } while (listResponse.isTruncated());

        // Also delete all object versions if versioning was enabled
        try {
            var versionsRequest = ListObjectVersionsRequest.builder().bucket(bucket).build();
            var versionsResponse = s3Client.listObjectVersions(versionsRequest);

            var versions = new ArrayList<ObjectIdentifier>();
            for (var v : versionsResponse.versions()) {
                versions.add(ObjectIdentifier.builder()
                        .key(v.key())
                        .versionId(v.versionId())
                        .build());
            }
            for (var dm : versionsResponse.deleteMarkers()) {
                versions.add(ObjectIdentifier.builder()
                        .key(dm.key())
                        .versionId(dm.versionId())
                        .build());
            }

            if (!versions.isEmpty()) {
                s3Client.deleteObjects(DeleteObjectsRequest.builder()
                        .bucket(bucket)
                        .delete(Delete.builder().objects(versions).build())
                        .build());
            }
        } catch (Exception e) {
            // Versioning might not be enabled, ignore
            log.debug("Could not delete object versions: {}", e.getMessage());
        }
    }

    private S3BucketResource mapBucketToResource(String bucket) {
        var resource = new S3BucketResource();
        resource.setBucket(bucket);

        // Get bucket location
        try {
            var locationResponse = s3Client.getBucketLocation(GetBucketLocationRequest.builder()
                    .bucket(bucket)
                    .build());
            var location = locationResponse.locationConstraintAsString();
            resource.setRegion(location != null && !location.isEmpty() ? location : "us-east-1");
        } catch (Exception e) {
            resource.setRegion(defaultRegion);
        }

        // Get versioning status
        try {
            var versioningResponse = s3Client.getBucketVersioning(GetBucketVersioningRequest.builder()
                    .bucket(bucket)
                    .build());
            resource.setVersioning(versioningResponse.status() == BucketVersioningStatus.ENABLED);
        } catch (Exception e) {
            resource.setVersioning(false);
        }

        // Get encryption
        try {
            var encryptionResponse = s3Client.getBucketEncryption(GetBucketEncryptionRequest.builder()
                    .bucket(bucket)
                    .build());
            var rules = encryptionResponse.serverSideEncryptionConfiguration().rules();
            if (!rules.isEmpty()) {
                var sse = rules.get(0).applyServerSideEncryptionByDefault();
                if (sse.sseAlgorithm() == ServerSideEncryption.AES256) {
                    resource.setServerSideEncryption("AES256");
                } else if (sse.sseAlgorithm() == ServerSideEncryption.AWS_KMS) {
                    resource.setServerSideEncryption("aws:kms");
                    resource.setKmsKeyId(sse.kmsMasterKeyID());
                }
            }
        } catch (Exception e) {
            // No encryption configured
        }

        // Get tags
        try {
            var tagsResponse = s3Client.getBucketTagging(GetBucketTaggingRequest.builder()
                    .bucket(bucket)
                    .build());
            resource.setTags(tagsResponse.tagSet().stream()
                    .collect(Collectors.toMap(Tag::key, Tag::value)));
        } catch (Exception e) {
            // No tags
        }

        // Set cloud-managed properties
        var region = resource.getRegion() != null ? resource.getRegion() : defaultRegion;
        resource.setArn("arn:aws:s3:::" + bucket);
        resource.setDomainName(bucket + ".s3.amazonaws.com");
        resource.setRegionalDomainName(bucket + ".s3." + region + ".amazonaws.com");

        // Get website endpoint if configured
        try {
            s3Client.getBucketWebsite(GetBucketWebsiteRequest.builder()
                    .bucket(bucket)
                    .build());
            resource.setWebsiteEndpoint(bucket + ".s3-website-" + region + ".amazonaws.com");
        } catch (Exception e) {
            // Website not configured
        }

        return resource;
    }
}
