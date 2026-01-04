package cloud.kitelang.provider.aws.iam;

import cloud.kitelang.api.annotations.Cloud;
import cloud.kitelang.api.annotations.Property;
import cloud.kitelang.api.annotations.TypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * AWS IAM Policy - a document that defines permissions for AWS resources.
 *
 * Example usage:
 * <pre>
 * resource IamPolicy s3Access {
 *     name = "s3-bucket-access"
 *     description = "Policy for accessing S3 buckets"
 *     policy = """
 *         {
 *             "Version": "2012-10-17",
 *             "Statement": [
 *                 {
 *                     "Effect": "Allow",
 *                     "Action": [
 *                         "s3:GetObject",
 *                         "s3:PutObject"
 *                     ],
 *                     "Resource": "arn:aws:s3:::my-bucket/*"
 *                 }
 *             ]
 *         }
 *     """
 *     tags = {
 *         Environment: "production"
 *     }
 * }
 * </pre>
 *
 * @see <a href="https://docs.aws.amazon.com/IAM/latest/UserGuide/access_policies.html">AWS IAM Policies Documentation</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("IamPolicy")
public class IamPolicyResource {

    @Property(description = "Name of the policy", optional = false)
    private String name;

    @Property(description = "Description of the policy")
    private String description;

    @Property(description = "Path for the policy (default: /)")
    private String path;

    @Property(description = "The JSON policy document", optional = false)
    private String policy;

    @Property(description = "Tags to apply to the policy")
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    @Cloud
    @Property(description = "The stable and unique ID for the policy")
    private String policyId;

    @Cloud(importable = true)
    @Property(description = "The Amazon Resource Name (ARN) of the policy")
    private String arn;

    @Cloud
    @Property(description = "The date and time when the policy was created")
    private String createDate;

    @Cloud
    @Property(description = "The number of entities (users, groups, roles) attached to this policy")
    private Integer attachmentCount;

    @Cloud
    @Property(description = "The default version ID of the policy")
    private String defaultVersionId;
}
