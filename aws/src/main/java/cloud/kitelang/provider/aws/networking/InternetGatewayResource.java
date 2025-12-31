package cloud.kitelang.provider.aws.networking;

import cloud.kitelang.api.annotations.Cloud;
import cloud.kitelang.api.annotations.Property;
import cloud.kitelang.api.annotations.TypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * AWS Internet Gateway - enables internet access for VPCs.
 *
 * Example usage:
 * <pre>
 * resource InternetGateway main {
 *     vpcId = vpc.vpcId
 *     tags = {
 *         Name: "main-igw",
 *         Environment: "production"
 *     }
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("InternetGateway")
public class InternetGatewayResource {

    @Property(description = "The VPC ID to attach the internet gateway to")
    private String vpcId;

    @Property(description = "Tags to apply to the internet gateway")
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    @Cloud
    @Property(description = "The internet gateway ID assigned by AWS")
    private String internetGatewayId;

    @Cloud
    @Property(description = "The AWS account ID that owns the internet gateway")
    private String ownerId;

    @Cloud
    @Property(description = "Attachment state (available, attaching, detaching)")
    private String attachmentState;
}
