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
 * AWS IAM Instance Profile - a container for an IAM role that can be attached to EC2 instances.
 *
 * Example usage:
 * <pre>
 * resource IamInstanceProfile webProfile {
 *     name = "web-server-profile"
 *     role = iamRole.name
 *     tags = {
 *         Environment: "production"
 *     }
 * }
 *
 * resource Ec2Instance web {
 *     ami = "ami-0c55b159cbfafe1f0"
 *     instanceType = "t3.micro"
 *     iamInstanceProfile = webProfile.name
 * }
 * </pre>
 *
 * @see <a href="https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_use_switch-role-ec2_instance-profiles.html">AWS Instance Profiles Documentation</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("IamInstanceProfile")
public class IamInstanceProfileResource {

    @Property(description = "Name of the instance profile", optional = false)
    private String name;

    @Property(description = "Path for the instance profile (default: /)")
    private String path;

    @Property(description = "Name of the IAM role to associate with the instance profile")
    private String role;

    @Property(description = "Tags to apply to the instance profile")
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    @Cloud
    @Property(description = "The stable and unique ID for the instance profile")
    private String instanceProfileId;

    @Cloud(importable = true)
    @Property(description = "The Amazon Resource Name (ARN) of the instance profile")
    private String arn;

    @Cloud
    @Property(description = "The date and time when the instance profile was created")
    private String createDate;
}
