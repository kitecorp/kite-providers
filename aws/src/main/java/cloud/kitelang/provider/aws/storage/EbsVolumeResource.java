package cloud.kitelang.provider.aws.storage;

import cloud.kitelang.api.annotations.Cloud;
import cloud.kitelang.api.annotations.Property;
import cloud.kitelang.api.annotations.TypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * AWS EBS Volume resource.
 *
 * Example usage:
 * <pre>
 * resource EbsVolume data {
 *     availability_zone = "us-east-1a"
 *     size = 100
 *     volume_type = "gp3"
 *     iops = 3000
 *     throughput = 125
 *     encrypted = true
 *     tags = {
 *         Name: "data-volume"
 *     }
 * }
 * </pre>
 *
 * @see <a href="https://docs.aws.amazon.com/ebs/latest/userguide/what-is-ebs.html">AWS EBS Documentation</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("EbsVolume")
public class EbsVolumeResource {

    @Property(description = "The Availability Zone in which to create the volume", optional = false)
    private String availabilityZone;

    @Property(description = "The size of the volume in GiBs", optional = false)
    private Integer size;

    @Property(description = "The volume type",
              validValues = {"gp2", "gp3", "io1", "io2", "st1", "sc1", "standard"})
    private String volumeType = "gp3";

    @Property(description = "The number of I/O operations per second (IOPS)")
    private Integer iops;

    @Property(description = "The throughput in MiB/s. Only valid for gp3 volumes")
    private Integer throughput;

    @Property(description = "Whether the volume should be encrypted")
    private Boolean encrypted = false;

    @Property(description = "The ARN of the KMS key to use for encryption")
    private String kmsKeyId;

    @Property(description = "The snapshot ID to create the volume from")
    private String snapshotId;

    @Property(description = "Enable Multi-Attach for io1/io2 volumes")
    private Boolean multiAttachEnabled = false;

    @Property(description = "Tags to apply to the volume")
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    @Cloud
    @Property(description = "The volume ID assigned by AWS")
    private String volumeId;

    @Cloud(importable = true)
    @Property(description = "The Amazon Resource Name (ARN) of the volume")
    private String arn;

    @Cloud
    @Property(description = "The current state of the volume")
    private String state;

    @Cloud
    @Property(description = "The time the volume was created")
    private String createTime;

    @Cloud
    @Property(description = "Information about the volume attachments")
    private String attachments;
}
