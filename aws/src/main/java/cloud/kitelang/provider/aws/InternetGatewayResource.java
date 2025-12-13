package cloud.kitelang.provider.aws;

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

    /**
     * The VPC ID to attach the internet gateway to.
     * Optional - can be attached after creation.
     */
    @Property
    private String vpcId;

    /**
     * Tags to apply to the internet gateway.
     */
    @Property
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    /**
     * The internet gateway ID assigned by AWS.
     */
    @Cloud
    @Property
    private String internetGatewayId;

    /**
     * The AWS account ID that owns the internet gateway.
     */
    @Cloud
    @Property
    private String ownerId;

    /**
     * The current state of the attachment (available, attaching, detaching).
     */
    @Cloud
    @Property
    private String attachmentState;
}
