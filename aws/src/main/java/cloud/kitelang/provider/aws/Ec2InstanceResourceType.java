package cloud.kitelang.provider.aws;

import cloud.kitelang.provider.Diagnostic;
import cloud.kitelang.provider.ResourceTypeHandler;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ResourceTypeHandler for AWS EC2 Instance.
 * Implements CRUD operations for EC2 instances using AWS SDK.
 */
@Slf4j
public class Ec2InstanceResourceType extends ResourceTypeHandler<Ec2InstanceResource> {

    private final Ec2Client ec2Client;

    public Ec2InstanceResourceType() {
        this.ec2Client = Ec2Client.create();
    }

    public Ec2InstanceResourceType(Ec2Client ec2Client) {
        this.ec2Client = ec2Client;
    }

    @Override
    public Ec2InstanceResource create(Ec2InstanceResource resource) {
        log.info("Creating EC2 instance with AMI '{}' and type '{}'",
                resource.getAmi(), resource.getInstanceType());

        var requestBuilder = RunInstancesRequest.builder()
                .imageId(resource.getAmi())
                .instanceType(InstanceType.fromValue(resource.getInstanceType()))
                .minCount(1)
                .maxCount(1);

        // Subnet and network configuration
        if (resource.getSubnetId() != null) {
            var networkInterface = InstanceNetworkInterfaceSpecification.builder()
                    .deviceIndex(0)
                    .subnetId(resource.getSubnetId())
                    .associatePublicIpAddress(resource.getAssociatePublicIpAddress())
                    .groups(resource.getSecurityGroupIds())
                    .build();
            requestBuilder.networkInterfaces(networkInterface);
        } else if (resource.getSecurityGroupIds() != null) {
            requestBuilder.securityGroupIds(resource.getSecurityGroupIds());
        }

        // Key pair
        if (resource.getKeyName() != null) {
            requestBuilder.keyName(resource.getKeyName());
        }

        // IAM instance profile
        if (resource.getIamInstanceProfile() != null) {
            var profile = IamInstanceProfileSpecification.builder();
            if (resource.getIamInstanceProfile().startsWith("arn:")) {
                profile.arn(resource.getIamInstanceProfile());
            } else {
                profile.name(resource.getIamInstanceProfile());
            }
            requestBuilder.iamInstanceProfile(profile.build());
        }

        // User data
        if (resource.getUserData() != null) {
            String encoded = Base64.getEncoder().encodeToString(resource.getUserData().getBytes());
            requestBuilder.userData(encoded);
        }

        // Availability zone
        if (resource.getAvailabilityZone() != null) {
            requestBuilder.placement(Placement.builder()
                    .availabilityZone(resource.getAvailabilityZone())
                    .tenancy(resource.getTenancy() != null ?
                            Tenancy.fromValue(resource.getTenancy()) : null)
                    .build());
        } else if (resource.getTenancy() != null) {
            requestBuilder.placement(Placement.builder()
                    .tenancy(Tenancy.fromValue(resource.getTenancy()))
                    .build());
        }

        // Monitoring
        if (resource.getMonitoring() != null && resource.getMonitoring()) {
            requestBuilder.monitoring(RunInstancesMonitoringEnabled.builder()
                    .enabled(true)
                    .build());
        }

        // Root volume configuration
        if (resource.getRootVolumeSize() != null || resource.getRootVolumeType() != null) {
            var ebsBuilder = EbsBlockDevice.builder();
            if (resource.getRootVolumeSize() != null) {
                ebsBuilder.volumeSize(resource.getRootVolumeSize());
            }
            if (resource.getRootVolumeType() != null) {
                ebsBuilder.volumeType(VolumeType.fromValue(resource.getRootVolumeType()));
            }
            if (resource.getDeleteRootVolumeOnTermination() != null) {
                ebsBuilder.deleteOnTermination(resource.getDeleteRootVolumeOnTermination());
            } else {
                ebsBuilder.deleteOnTermination(true);
            }

            // Get root device name from AMI
            String rootDeviceName = getRootDeviceName(resource.getAmi());
            var blockDevice = BlockDeviceMapping.builder()
                    .deviceName(rootDeviceName)
                    .ebs(ebsBuilder.build())
                    .build();
            requestBuilder.blockDeviceMappings(blockDevice);
        }

        // Tags during creation
        if (resource.getTags() != null && !resource.getTags().isEmpty()) {
            var tagSpecs = TagSpecification.builder()
                    .resourceType(ResourceType.INSTANCE)
                    .tags(resource.getTags().entrySet().stream()
                            .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                            .collect(Collectors.toList()))
                    .build();
            requestBuilder.tagSpecifications(tagSpecs);
        }

        var response = ec2Client.runInstances(requestBuilder.build());
        var instance = response.instances().get(0);
        log.info("Created EC2 instance: {} (state: {})",
                instance.instanceId(), instance.state().nameAsString());

        resource.setInstanceId(instance.instanceId());

        // Wait for instance to be running
        waitForInstance(instance.instanceId(), "running");

        return read(resource);
    }

    private String getRootDeviceName(String amiId) {
        try {
            var response = ec2Client.describeImages(DescribeImagesRequest.builder()
                    .imageIds(amiId)
                    .build());
            if (!response.images().isEmpty()) {
                return response.images().get(0).rootDeviceName();
            }
        } catch (Exception e) {
            log.warn("Could not get root device name for AMI {}: {}", amiId, e.getMessage());
        }
        return "/dev/xvda"; // Default fallback
    }

    private void waitForInstance(String instanceId, String targetState) {
        log.info("Waiting for instance '{}' to reach state '{}'", instanceId, targetState);

        int maxAttempts = 60;
        int attempt = 0;

        while (attempt < maxAttempts) {
            try {
                var response = ec2Client.describeInstances(DescribeInstancesRequest.builder()
                        .instanceIds(instanceId)
                        .build());

                if (!response.reservations().isEmpty() &&
                    !response.reservations().get(0).instances().isEmpty()) {
                    String state = response.reservations().get(0).instances().get(0)
                            .state().nameAsString();
                    if (targetState.equals(state)) {
                        log.info("Instance '{}' is now '{}'", instanceId, state);
                        return;
                    }
                    if ("terminated".equals(state) || "shutting-down".equals(state)) {
                        throw new RuntimeException("Instance entered unexpected state: " + state);
                    }
                }

                Thread.sleep(5000);
                attempt++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for instance", e);
            }
        }

        log.warn("Timed out waiting for instance '{}' to reach state '{}'", instanceId, targetState);
    }

    @Override
    public Ec2InstanceResource read(Ec2InstanceResource resource) {
        if (resource.getInstanceId() == null) {
            log.warn("Cannot read EC2 instance without instanceId");
            return null;
        }

        log.info("Reading EC2 instance: {}", resource.getInstanceId());

        try {
            var response = ec2Client.describeInstances(DescribeInstancesRequest.builder()
                    .instanceIds(resource.getInstanceId())
                    .build());

            if (response.reservations().isEmpty() ||
                response.reservations().get(0).instances().isEmpty()) {
                return null;
            }

            var instance = response.reservations().get(0).instances().get(0);

            // Return null if terminated
            if ("terminated".equals(instance.state().nameAsString())) {
                return null;
            }

            return mapToResource(instance);

        } catch (Ec2Exception e) {
            if (e.awsErrorDetails().errorCode().equals("InvalidInstanceID.NotFound")) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public Ec2InstanceResource update(Ec2InstanceResource resource) {
        log.info("Updating EC2 instance: {}", resource.getInstanceId());

        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("EC2 instance not found: " + resource.getInstanceId());
        }

        String instanceId = resource.getInstanceId();

        // Update instance type (requires stop/start)
        if (resource.getInstanceType() != null &&
            !resource.getInstanceType().equals(current.getInstanceType())) {
            log.info("Changing instance type from {} to {}",
                    current.getInstanceType(), resource.getInstanceType());

            // Stop instance
            ec2Client.stopInstances(StopInstancesRequest.builder()
                    .instanceIds(instanceId)
                    .build());
            waitForInstance(instanceId, "stopped");

            // Modify instance type
            ec2Client.modifyInstanceAttribute(ModifyInstanceAttributeRequest.builder()
                    .instanceId(instanceId)
                    .instanceType(AttributeValue.builder()
                            .value(resource.getInstanceType())
                            .build())
                    .build());

            // Start instance
            ec2Client.startInstances(StartInstancesRequest.builder()
                    .instanceIds(instanceId)
                    .build());
            waitForInstance(instanceId, "running");
        }

        // Update monitoring
        if (resource.getMonitoring() != null) {
            if (resource.getMonitoring()) {
                ec2Client.monitorInstances(MonitorInstancesRequest.builder()
                        .instanceIds(instanceId)
                        .build());
            } else {
                ec2Client.unmonitorInstances(UnmonitorInstancesRequest.builder()
                        .instanceIds(instanceId)
                        .build());
            }
        }

        // Update security groups (VPC only)
        if (resource.getSecurityGroupIds() != null && current.getSubnetId() != null) {
            ec2Client.modifyInstanceAttribute(ModifyInstanceAttributeRequest.builder()
                    .instanceId(instanceId)
                    .groups(resource.getSecurityGroupIds())
                    .build());
        }

        // Update tags
        if (resource.getTags() != null) {
            if (current.getTags() != null && !current.getTags().isEmpty()) {
                var oldTags = current.getTags().entrySet().stream()
                        .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                        .collect(Collectors.toList());
                ec2Client.deleteTags(DeleteTagsRequest.builder()
                        .resources(instanceId)
                        .tags(oldTags)
                        .build());
            }
            if (!resource.getTags().isEmpty()) {
                var newTags = resource.getTags().entrySet().stream()
                        .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                        .collect(Collectors.toList());
                ec2Client.createTags(CreateTagsRequest.builder()
                        .resources(instanceId)
                        .tags(newTags)
                        .build());
            }
        }

        return read(resource);
    }

    @Override
    public boolean delete(Ec2InstanceResource resource) {
        if (resource.getInstanceId() == null) {
            log.warn("Cannot terminate EC2 instance without instanceId");
            return false;
        }

        log.info("Terminating EC2 instance: {}", resource.getInstanceId());

        try {
            ec2Client.terminateInstances(TerminateInstancesRequest.builder()
                    .instanceIds(resource.getInstanceId())
                    .build());

            log.info("Terminated EC2 instance: {}", resource.getInstanceId());
            return true;

        } catch (Ec2Exception e) {
            if (e.awsErrorDetails().errorCode().equals("InvalidInstanceID.NotFound")) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public List<Diagnostic> validate(Ec2InstanceResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getAmi() == null || resource.getAmi().isBlank()) {
            diagnostics.add(Diagnostic.error("ami is required")
                    .withProperty("ami"));
        }

        if (resource.getInstanceType() == null || resource.getInstanceType().isBlank()) {
            diagnostics.add(Diagnostic.error("instanceType is required")
                    .withProperty("instanceType"));
        }

        // Validate tenancy
        if (resource.getTenancy() != null && !resource.getTenancy().isBlank()) {
            if (!resource.getTenancy().equals("default") &&
                !resource.getTenancy().equals("dedicated") &&
                !resource.getTenancy().equals("host")) {
                diagnostics.add(Diagnostic.error("tenancy must be 'default', 'dedicated', or 'host'")
                        .withProperty("tenancy"));
            }
        }

        // Validate volume type
        if (resource.getRootVolumeType() != null && !resource.getRootVolumeType().isBlank()) {
            List<String> validTypes = List.of("gp2", "gp3", "io1", "io2", "st1", "sc1", "standard");
            if (!validTypes.contains(resource.getRootVolumeType())) {
                diagnostics.add(Diagnostic.error("rootVolumeType must be one of: " +
                        String.join(", ", validTypes))
                        .withProperty("rootVolumeType"));
            }
        }

        return diagnostics;
    }

    private Ec2InstanceResource mapToResource(Instance instance) {
        var resource = new Ec2InstanceResource();

        // Input properties
        resource.setAmi(instance.imageId());
        resource.setInstanceType(instance.instanceTypeAsString());
        resource.setSubnetId(instance.subnetId());
        resource.setKeyName(instance.keyName());

        if (instance.securityGroups() != null) {
            resource.setSecurityGroupIds(instance.securityGroups().stream()
                    .map(GroupIdentifier::groupId)
                    .collect(Collectors.toList()));
        }

        if (instance.iamInstanceProfile() != null) {
            resource.setIamInstanceProfile(instance.iamInstanceProfile().arn());
        }

        if (instance.placement() != null) {
            resource.setAvailabilityZone(instance.placement().availabilityZone());
            if (instance.placement().tenancy() != null) {
                resource.setTenancy(instance.placement().tenancy().toString());
            }
        }

        if (instance.monitoring() != null) {
            resource.setMonitoring("enabled".equals(instance.monitoring().stateAsString()));
        }

        // Tags
        if (instance.tags() != null && !instance.tags().isEmpty()) {
            resource.setTags(instance.tags().stream()
                    .collect(Collectors.toMap(Tag::key, Tag::value)));
        }

        // Cloud-managed properties
        resource.setInstanceId(instance.instanceId());
        resource.setState(instance.state().nameAsString());
        resource.setPublicIp(instance.publicIpAddress());
        resource.setPrivateIp(instance.privateIpAddress());
        resource.setPublicDnsName(instance.publicDnsName());
        resource.setPrivateDnsName(instance.privateDnsName());
        resource.setVpcId(instance.vpcId());
        resource.setArchitecture(instance.architectureAsString());
        resource.setRootDeviceName(instance.rootDeviceName());

        return resource;
    }
}
