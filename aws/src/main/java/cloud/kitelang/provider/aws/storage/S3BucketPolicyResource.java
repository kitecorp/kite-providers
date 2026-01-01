package cloud.kitelang.provider.aws.storage;

import cloud.kitelang.api.annotations.Property;
import cloud.kitelang.api.annotations.TypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AWS S3 Bucket Policy resource.
 *
 * Example usage:
 * <pre>
 * resource S3BucketPolicy allow_public_read {
 *     bucket = bucket.bucket
 *     policy = """
 *     {
 *         "Version": "2012-10-17",
 *         "Statement": [
 *             {
 *                 "Sid": "PublicReadGetObject",
 *                 "Effect": "Allow",
 *                 "Principal": "*",
 *                 "Action": "s3:GetObject",
 *                 "Resource": "arn:aws:s3:::my-bucket/*"
 *             }
 *         ]
 *     }
 *     """
 * }
 *
 * // CloudFront access policy
 * resource S3BucketPolicy cloudfront_access {
 *     bucket = bucket.bucket
 *     policy = """
 *     {
 *         "Version": "2012-10-17",
 *         "Statement": [
 *             {
 *                 "Sid": "AllowCloudFrontAccess",
 *                 "Effect": "Allow",
 *                 "Principal": {
 *                     "Service": "cloudfront.amazonaws.com"
 *                 },
 *                 "Action": "s3:GetObject",
 *                 "Resource": "arn:aws:s3:::my-bucket/*",
 *                 "Condition": {
 *                     "StringEquals": {
 *                         "AWS:SourceArn": "arn:aws:cloudfront::123456789:distribution/EXXX"
 *                     }
 *                 }
 *             }
 *         ]
 *     }
 *     """
 * }
 * </pre>
 *
 * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/userguide/bucket-policies.html">AWS S3 Bucket Policy Documentation</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("S3BucketPolicy")
public class S3BucketPolicyResource {

    @Property(description = "The name of the bucket to apply the policy to")
    private String bucket;

    @Property(description = "The bucket policy as a JSON string (IAM policy document)")
    private String policy;
}
