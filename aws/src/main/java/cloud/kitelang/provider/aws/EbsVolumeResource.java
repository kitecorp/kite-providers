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
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("EbsVolume")
public class EbsVolumeResource {

    /**
     * The Availability Zone in which to create the volume.
     * Required.
     */
    @Property
    private String availabilityZone;

    /**
     * The size of the volume in GiBs.
     * Required unless snapshotId is specified.
     */
    @Property
    private Integer size;

    /**
     * The volume type.
     * Valid values: gp2, gp3, io1, io2, st1, sc1, standard.
     * Default: gp3
     */
    @Property
    private String volumeType;

    /**
     * The number of I/O operations per second (IOPS).
     * Only valid for io1, io2, and gp3 volumes.
     * For gp3: 3,000 - 16,000 IOPS
     * For io1: 100 - 64,000 IOPS
     * For io2: 100 - 256,000 IOPS
     */
    @Property
    private Integer iops;

    /**
     * The throughput in MiB/s.
     * Only valid for gp3 volumes.
     * Range: 125 - 1,000 MiB/s
     */
    @Property
    private Integer throughput;

    /**
     * Whether the volume should be encrypted.
     * Default: false
     */
    @Property
    private Boolean encrypted;

    /**
     * The ARN of the KMS key to use for encryption.
     * If not specified, the default EBS encryption key is used.
     */
    @Property
    private String kmsKeyId;

    /**
     * The snapshot ID to create the volume from.
     * Optional.
     */
    @Property
    private String snapshotId;

    /**
     * Whether to enable Multi-Attach for io1/io2 volumes.
     * Allows the volume to be attached to multiple instances.
     */
    @Property
    private Boolean multiAttachEnabled;

    /**
     * Tags to apply to the volume.
     */
    @Property
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    /**
     * The volume ID assigned by AWS.
     */
    @Cloud
    @Property
    private String volumeId;

    /**
     * The Amazon Resource Name (ARN) of the volume.
     */
    @Cloud
    @Property
    private String arn;

    /**
     * The current state of the volume.
     * Values: creating, available, in-use, deleting, deleted, error
     */
    @Cloud
    @Property
    private String state;

    /**
     * The time the volume was created.
     */
    @Cloud
    @Property
    private String createTime;

    /**
     * Information about the volume attachments.
     */
    @Cloud
    @Property
    private String attachments;
}
