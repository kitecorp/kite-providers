package cloud.kitelang.provider.aws;

import cloud.kitelang.provider.Diagnostic;
import cloud.kitelang.provider.ResourceTypeHandler;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * ResourceTypeHandler for AWS S3 Bucket Policy.
 * Implements CRUD operations using AWS S3 SDK.
 */
@Slf4j
public class S3BucketPolicyResourceType extends ResourceTypeHandler<S3BucketPolicyResource> {

    private final S3Client s3Client;

    public S3BucketPolicyResourceType() {
        var region = System.getenv("AWS_REGION") != null
                ? System.getenv("AWS_REGION")
                : "us-east-1";
        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .build();
    }

    public S3BucketPolicyResourceType(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    public S3BucketPolicyResource create(S3BucketPolicyResource resource) {
        log.info("Creating S3 Bucket Policy for: {}", resource.getBucket());

        s3Client.putBucketPolicy(PutBucketPolicyRequest.builder()
                .bucket(resource.getBucket())
                .policy(resource.getPolicy())
                .build());

        log.info("Created S3 Bucket Policy for: {}", resource.getBucket());

        return read(resource);
    }

    @Override
    public S3BucketPolicyResource read(S3BucketPolicyResource resource) {
        if (resource.getBucket() == null) {
            log.warn("Cannot read S3 Bucket Policy without bucket name");
            return null;
        }

        log.info("Reading S3 Bucket Policy for: {}", resource.getBucket());

        try {
            var response = s3Client.getBucketPolicy(GetBucketPolicyRequest.builder()
                    .bucket(resource.getBucket())
                    .build());

            var result = new S3BucketPolicyResource();
            result.setBucket(resource.getBucket());
            result.setPolicy(response.policy());
            return result;

        } catch (S3Exception e) {
            if (e.statusCode() == 404 || "NoSuchBucketPolicy".equals(e.awsErrorDetails().errorCode())) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public S3BucketPolicyResource update(S3BucketPolicyResource resource) {
        log.info("Updating S3 Bucket Policy for: {}", resource.getBucket());

        // Put replaces the existing policy
        s3Client.putBucketPolicy(PutBucketPolicyRequest.builder()
                .bucket(resource.getBucket())
                .policy(resource.getPolicy())
                .build());

        return read(resource);
    }

    @Override
    public boolean delete(S3BucketPolicyResource resource) {
        if (resource.getBucket() == null) {
            log.warn("Cannot delete S3 Bucket Policy without bucket name");
            return false;
        }

        log.info("Deleting S3 Bucket Policy for: {}", resource.getBucket());

        try {
            s3Client.deleteBucketPolicy(DeleteBucketPolicyRequest.builder()
                    .bucket(resource.getBucket())
                    .build());

            log.info("Deleted S3 Bucket Policy for: {}", resource.getBucket());
            return true;

        } catch (S3Exception e) {
            if (e.statusCode() == 404 || "NoSuchBucketPolicy".equals(e.awsErrorDetails().errorCode())) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public List<Diagnostic> validate(S3BucketPolicyResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getBucket() == null || resource.getBucket().isBlank()) {
            diagnostics.add(Diagnostic.error("bucket is required")
                    .withProperty("bucket"));
        }

        if (resource.getPolicy() == null || resource.getPolicy().isBlank()) {
            diagnostics.add(Diagnostic.error("policy is required")
                    .withProperty("policy"));
        } else {
            // Basic JSON validation
            var policy = resource.getPolicy().trim();
            if (!policy.startsWith("{") || !policy.endsWith("}")) {
                diagnostics.add(Diagnostic.error("policy must be a valid JSON object")
                        .withProperty("policy"));
            }
        }

        return diagnostics;
    }
}
