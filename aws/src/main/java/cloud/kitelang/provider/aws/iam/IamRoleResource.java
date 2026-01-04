package cloud.kitelang.provider.aws.iam;

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
 * AWS IAM Role - an identity with specific permissions that can be assumed by trusted entities.
 *
 * Example usage:
 * <pre>
 * resource IamRole ec2Role {
 *     name = "ec2-instance-role"
 *     description = "Role for EC2 instances"
 *     assumeRolePolicy = """
 *         {
 *             "Version": "2012-10-17",
 *             "Statement": [
 *                 {
 *                     "Effect": "Allow",
 *                     "Principal": {
 *                         "Service": "ec2.amazonaws.com"
 *                     },
 *                     "Action": "sts:AssumeRole"
 *                 }
 *             ]
 *         }
 *     """
 *     managedPolicyArns = [
 *         "arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess"
 *     ]
 *     tags = {
 *         Environment: "production"
 *     }
 * }
 * </pre>
 *
 * @see <a href="https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles.html">AWS IAM Roles Documentation</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("IamRole")
public class IamRoleResource {

    @Property(description = "Name of the IAM role", optional = false)
    private String name;

    @Property(description = "Description of the role")
    private String description;

    @Property(description = "The trust policy that grants an entity permission to assume the role (JSON)", optional = false)
    private String assumeRolePolicy;

    @Property(description = "Path for the role (default: /)")
    private String path;

    @Property(description = "Maximum session duration in seconds (3600-43200)")
    private Integer maxSessionDuration;

    @Property(description = "ARNs of AWS managed policies to attach")
    private List<String> managedPolicyArns;

    @Property(description = "Inline policies to embed in the role (name -> policy JSON)")
    private Map<String, String> inlinePolicies;

    @Property(description = "Tags to apply to the role")
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    @Cloud
    @Property(description = "The stable and unique ID for the role")
    private String roleId;

    @Cloud(importable = true)
    @Property(description = "The Amazon Resource Name (ARN) of the role")
    private String arn;

    @Cloud
    @Property(description = "The date and time when the role was created")
    private String createDate;
}
