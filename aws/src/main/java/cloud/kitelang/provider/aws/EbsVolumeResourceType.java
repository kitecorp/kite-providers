package cloud.kitelang.provider.aws;

import cloud.kitelang.provider.Diagnostic;
import cloud.kitelang.provider.ResourceTypeHandler;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ResourceTypeHandler for AWS EBS Volumes.
 * Implements CRUD operations for EBS volumes using AWS EC2 SDK.
 */
@Slf4j
public class EbsVolumeResourceType extends ResourceTypeHandler<EbsVolumeResource> {

    private static final Set<String> VALID_VOLUME_TYPES = Set.of(
            "gp2", "gp3", "io1", "io2", "st1", "sc1", "standard"
    );

    private static final Set<String> IOPS_VOLUME_TYPES = Set.of("gp3", "io1", "io2");

    private final Ec2Client ec2Client;

    public EbsVolumeResourceType() {
        this.ec2Client = Ec2Client.create();
    }

    public EbsVolumeResourceType(Ec2Client ec2Client) {
        this.ec2Client = ec2Client;
    }

    @Override
    public EbsVolumeResource create(EbsVolumeResource resource) {
        log.info("Creating EBS volume in AZ: {}, size: {} GiB, type: {}",
                resource.getAvailabilityZone(), resource.getSize(), resource.getVolumeType());

        var requestBuilder = CreateVolumeRequest.builder()
                .availabilityZone(resource.getAvailabilityZone());

        // Size (required unless snapshotId is specified)
        if (resource.getSize() != null) {
            requestBuilder.size(resource.getSize());
        }

        // Volume type (default to gp3)
        var volumeType = resource.getVolumeType() != null ? resource.getVolumeType() : "gp3";
        requestBuilder.volumeType(VolumeType.fromValue(volumeType));

        // IOPS (for gp3, io1, io2)
        if (resource.getIops() != null && IOPS_VOLUME_TYPES.contains(volumeType)) {
            requestBuilder.iops(resource.getIops());
        }

        // Throughput (only for gp3)
        if (resource.getThroughput() != null && "gp3".equals(volumeType)) {
            requestBuilder.throughput(resource.getThroughput());
        }

        // Encryption
        if (resource.getEncrypted() != null) {
            requestBuilder.encrypted(resource.getEncrypted());
        }

        if (resource.getKmsKeyId() != null) {
            requestBuilder.kmsKeyId(resource.getKmsKeyId());
            requestBuilder.encrypted(true);
        }

        // Snapshot
        if (resource.getSnapshotId() != null) {
            requestBuilder.snapshotId(resource.getSnapshotId());
        }

        // Multi-Attach (io1/io2 only)
        if (resource.getMultiAttachEnabled() != null &&
            (volumeType.equals("io1") || volumeType.equals("io2"))) {
            requestBuilder.multiAttachEnabled(resource.getMultiAttachEnabled());
        }

        // Tags
        if (resource.getTags() != null && !resource.getTags().isEmpty()) {
            var tagSpecs = TagSpecification.builder()
                    .resourceType(ResourceType.VOLUME)
                    .tags(resource.getTags().entrySet().stream()
                            .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                            .collect(Collectors.toList()))
                    .build();
            requestBuilder.tagSpecifications(tagSpecs);
        }

        var response = ec2Client.createVolume(requestBuilder.build());

        log.info("Created EBS volume: {}", response.volumeId());

        // Set cloud-managed properties
        resource.setVolumeId(response.volumeId());
        resource.setState(response.stateAsString());
        resource.setCreateTime(response.createTime().toString());

        // Wait for volume to be available
        waitForVolumeAvailable(response.volumeId());

        return read(resource);
    }

    @Override
    public EbsVolumeResource read(EbsVolumeResource resource) {
        if (resource.getVolumeId() == null) {
            log.warn("Cannot read EBS volume without volumeId");
            return null;
        }

        log.info("Reading EBS volume: {}", resource.getVolumeId());

        try {
            var response = ec2Client.describeVolumes(DescribeVolumesRequest.builder()
                    .volumeIds(resource.getVolumeId())
                    .build());

            if (response.volumes().isEmpty()) {
                return null;
            }

            return mapVolumeToResource(response.volumes().get(0));

        } catch (Ec2Exception e) {
            if (e.awsErrorDetails().errorCode().equals("InvalidVolume.NotFound")) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public EbsVolumeResource update(EbsVolumeResource resource) {
        log.info("Updating EBS volume: {}", resource.getVolumeId());

        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("EBS volume not found: " + resource.getVolumeId());
        }

        // Check if we need to modify volume properties
        boolean needsModification = false;
        var modifyBuilder = ModifyVolumeRequest.builder()
                .volumeId(resource.getVolumeId());

        // Size can only be increased
        if (resource.getSize() != null && !resource.getSize().equals(current.getSize())) {
            if (resource.getSize() < current.getSize()) {
                throw new RuntimeException("Cannot decrease EBS volume size");
            }
            modifyBuilder.size(resource.getSize());
            needsModification = true;
        }

        // Volume type
        if (resource.getVolumeType() != null && !resource.getVolumeType().equals(current.getVolumeType())) {
            modifyBuilder.volumeType(VolumeType.fromValue(resource.getVolumeType()));
            needsModification = true;
        }

        // IOPS
        if (resource.getIops() != null && !resource.getIops().equals(current.getIops())) {
            modifyBuilder.iops(resource.getIops());
            needsModification = true;
        }

        // Throughput
        if (resource.getThroughput() != null && !resource.getThroughput().equals(current.getThroughput())) {
            modifyBuilder.throughput(resource.getThroughput());
            needsModification = true;
        }

        if (needsModification) {
            ec2Client.modifyVolume(modifyBuilder.build());
            log.info("Modified EBS volume: {}", resource.getVolumeId());

            // Wait for modification to complete
            waitForVolumeOptimizing(resource.getVolumeId());
        }

        // Update tags
        if (resource.getTags() != null) {
            deleteAllTags(resource.getVolumeId());
            if (!resource.getTags().isEmpty()) {
                applyTags(resource.getVolumeId(), resource.getTags());
            }
        }

        return read(resource);
    }

    @Override
    public boolean delete(EbsVolumeResource resource) {
        if (resource.getVolumeId() == null) {
            log.warn("Cannot delete EBS volume without volumeId");
            return false;
        }

        log.info("Deleting EBS volume: {}", resource.getVolumeId());

        try {
            ec2Client.deleteVolume(DeleteVolumeRequest.builder()
                    .volumeId(resource.getVolumeId())
                    .build());

            log.info("Deleted EBS volume: {}", resource.getVolumeId());
            return true;

        } catch (Ec2Exception e) {
            if (e.awsErrorDetails().errorCode().equals("InvalidVolume.NotFound")) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public List<Diagnostic> validate(EbsVolumeResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        // Availability zone is required
        if (resource.getAvailabilityZone() == null || resource.getAvailabilityZone().isBlank()) {
            diagnostics.add(Diagnostic.error("availability_zone is required")
                    .withProperty("availability_zone"));
        }

        // Size is required unless snapshotId is specified
        if (resource.getSize() == null && resource.getSnapshotId() == null) {
            diagnostics.add(Diagnostic.error("size is required when snapshot_id is not specified")
                    .withProperty("size"));
        }

        // Validate size range
        if (resource.getSize() != null) {
            if (resource.getSize() < 1 || resource.getSize() > 16384) {
                diagnostics.add(Diagnostic.error("size must be between 1 and 16384 GiB")
                        .withProperty("size"));
            }
        }

        // Validate volume type
        var volumeType = resource.getVolumeType() != null ? resource.getVolumeType() : "gp3";
        if (!VALID_VOLUME_TYPES.contains(volumeType)) {
            diagnostics.add(Diagnostic.error("Invalid volume_type",
                    "Valid values: " + String.join(", ", VALID_VOLUME_TYPES))
                    .withProperty("volume_type"));
        }

        // Validate IOPS
        if (resource.getIops() != null) {
            if (!IOPS_VOLUME_TYPES.contains(volumeType)) {
                diagnostics.add(Diagnostic.error("iops is only valid for gp3, io1, io2 volume types")
                        .withProperty("iops"));
            } else {
                validateIops(resource, volumeType, diagnostics);
            }
        }

        // Validate throughput (gp3 only)
        if (resource.getThroughput() != null) {
            if (!"gp3".equals(volumeType)) {
                diagnostics.add(Diagnostic.error("throughput is only valid for gp3 volume type")
                        .withProperty("throughput"));
            } else if (resource.getThroughput() < 125 || resource.getThroughput() > 1000) {
                diagnostics.add(Diagnostic.error("throughput must be between 125 and 1000 MiB/s")
                        .withProperty("throughput"));
            }
        }

        // Multi-attach only for io1/io2
        if (resource.getMultiAttachEnabled() != null && resource.getMultiAttachEnabled()) {
            if (!volumeType.equals("io1") && !volumeType.equals("io2")) {
                diagnostics.add(Diagnostic.error("multi_attach_enabled is only valid for io1/io2 volume types")
                        .withProperty("multi_attach_enabled"));
            }
        }

        return diagnostics;
    }

    private void validateIops(EbsVolumeResource resource, String volumeType, List<Diagnostic> diagnostics) {
        int iops = resource.getIops();

        switch (volumeType) {
            case "gp3":
                if (iops < 3000 || iops > 16000) {
                    diagnostics.add(Diagnostic.error("gp3 IOPS must be between 3,000 and 16,000")
                            .withProperty("iops"));
                }
                break;
            case "io1":
                if (iops < 100 || iops > 64000) {
                    diagnostics.add(Diagnostic.error("io1 IOPS must be between 100 and 64,000")
                            .withProperty("iops"));
                }
                break;
            case "io2":
                if (iops < 100 || iops > 256000) {
                    diagnostics.add(Diagnostic.error("io2 IOPS must be between 100 and 256,000")
                            .withProperty("iops"));
                }
                break;
        }
    }

    private void waitForVolumeAvailable(String volumeId) {
        log.debug("Waiting for volume {} to be available", volumeId);

        int maxAttempts = 60;
        int attempt = 0;

        while (attempt < maxAttempts) {
            var response = ec2Client.describeVolumes(DescribeVolumesRequest.builder()
                    .volumeIds(volumeId)
                    .build());

            if (!response.volumes().isEmpty()) {
                var state = response.volumes().get(0).state();
                if (state == VolumeState.AVAILABLE || state == VolumeState.IN_USE) {
                    log.debug("Volume {} is now {}", volumeId, state);
                    return;
                }
                if (state == VolumeState.ERROR) {
                    throw new RuntimeException("Volume creation failed: " + volumeId);
                }
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for volume", e);
            }
            attempt++;
        }

        throw new RuntimeException("Timeout waiting for volume to become available: " + volumeId);
    }

    private void waitForVolumeOptimizing(String volumeId) {
        log.debug("Waiting for volume {} modification to complete", volumeId);

        int maxAttempts = 120;
        int attempt = 0;

        while (attempt < maxAttempts) {
            var response = ec2Client.describeVolumesModifications(DescribeVolumesModificationsRequest.builder()
                    .volumeIds(volumeId)
                    .build());

            if (!response.volumesModifications().isEmpty()) {
                var mod = response.volumesModifications().get(0);
                var state = mod.modificationState();
                if (state == VolumeModificationState.COMPLETED || state == VolumeModificationState.OPTIMIZING) {
                    log.debug("Volume {} modification state: {}", volumeId, state);
                    return;
                }
                if (state == VolumeModificationState.FAILED) {
                    throw new RuntimeException("Volume modification failed: " + volumeId);
                }
            }

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for volume modification", e);
            }
            attempt++;
        }

        log.warn("Timeout waiting for volume modification, continuing anyway: {}", volumeId);
    }

    private void applyTags(String volumeId, java.util.Map<String, String> tags) {
        var tagList = tags.entrySet().stream()
                .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                .collect(Collectors.toList());

        ec2Client.createTags(CreateTagsRequest.builder()
                .resources(volumeId)
                .tags(tagList)
                .build());
    }

    private void deleteAllTags(String volumeId) {
        var response = ec2Client.describeVolumes(DescribeVolumesRequest.builder()
                .volumeIds(volumeId)
                .build());

        if (!response.volumes().isEmpty()) {
            var existingTags = response.volumes().get(0).tags();
            if (!existingTags.isEmpty()) {
                ec2Client.deleteTags(DeleteTagsRequest.builder()
                        .resources(volumeId)
                        .tags(existingTags)
                        .build());
            }
        }
    }

    private EbsVolumeResource mapVolumeToResource(Volume volume) {
        var resource = new EbsVolumeResource();

        // Input properties
        resource.setAvailabilityZone(volume.availabilityZone());
        resource.setSize(volume.size());
        resource.setVolumeType(volume.volumeTypeAsString());
        resource.setIops(volume.iops());
        resource.setThroughput(volume.throughput());
        resource.setEncrypted(volume.encrypted());
        resource.setKmsKeyId(volume.kmsKeyId());
        resource.setSnapshotId(volume.snapshotId());
        resource.setMultiAttachEnabled(volume.multiAttachEnabled());

        // Tags
        if (volume.tags() != null && !volume.tags().isEmpty()) {
            resource.setTags(volume.tags().stream()
                    .collect(Collectors.toMap(Tag::key, Tag::value)));
        }

        // Cloud-managed properties
        resource.setVolumeId(volume.volumeId());
        resource.setState(volume.stateAsString());
        resource.setCreateTime(volume.createTime() != null ? volume.createTime().toString() : null);

        // Format attachments info
        if (volume.attachments() != null && !volume.attachments().isEmpty()) {
            var attachmentInfo = volume.attachments().stream()
                    .map(a -> String.format("%s:%s", a.instanceId(), a.device()))
                    .collect(Collectors.joining(", "));
            resource.setAttachments(attachmentInfo);
        }

        return resource;
    }
}
