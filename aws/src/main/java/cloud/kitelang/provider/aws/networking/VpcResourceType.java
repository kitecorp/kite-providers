package cloud.kitelang.provider.aws.networking;

import cloud.kitelang.provider.Diagnostic;
import cloud.kitelang.provider.ResourceTypeHandler;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ResourceTypeHandler for AWS VPC.
 * Implements CRUD operations for VPCs using AWS EC2 SDK.
 */
@Slf4j
public class VpcResourceType extends ResourceTypeHandler<VpcResource> {

    private volatile Ec2Client ec2Client;

    public VpcResourceType() {
        // Client created lazily to pick up configuration
    }

    public VpcResourceType(Ec2Client ec2Client) {
        this.ec2Client = ec2Client;
    }

    /**
     * Get or create an EC2 client.
     * Creates the client lazily to allow provider configuration to be applied first.
     */
    private Ec2Client getClient() {
        if (ec2Client == null) {
            synchronized (this) {
                if (ec2Client == null) {
                    log.debug("Creating EC2 client with current AWS configuration");
                    ec2Client = Ec2Client.create();
                }
            }
        }
        return ec2Client;
    }

    @Override
    public VpcResource create(VpcResource resource) {
        log.info("Creating VPC with CIDR: {}", resource.getCidrBlock());

        var requestBuilder = CreateVpcRequest.builder()
                .cidrBlock(resource.getCidrBlock());

        if (resource.getInstanceTenancy() != null) {
            requestBuilder.instanceTenancy(Tenancy.fromValue(resource.getInstanceTenancy()));
        }

        if (resource.getIpv6CidrBlock() != null) {
            requestBuilder.amazonProvidedIpv6CidrBlock(true);
        }

        var response = getClient().createVpc(requestBuilder.build());
        var vpc = response.vpc();

        log.info("Created VPC: {}", vpc.vpcId());

        // Set cloud-managed properties
        resource.setVpcId(vpc.vpcId());
        resource.setState(vpc.stateAsString());
        resource.setOwnerId(vpc.ownerId());

        // Wait for VPC to be available
        waitForVpcAvailable(vpc.vpcId());

        // Configure DNS settings
        configureDnsSettings(vpc.vpcId(), resource);

        // Apply tags
        if (resource.getTags() != null && !resource.getTags().isEmpty()) {
            applyTags(vpc.vpcId(), resource.getTags());
        }

        // Refresh to get all cloud-managed properties
        return read(resource);
    }

    @Override
    public VpcResource read(VpcResource resource) {
        if (resource.getVpcId() == null) {
            log.warn("Cannot read VPC without vpcId");
            return null;
        }

        log.info("Reading VPC: {}", resource.getVpcId());

        try {
            var response = getClient().describeVpcs(DescribeVpcsRequest.builder()
                    .vpcIds(resource.getVpcId())
                    .build());

            if (response.vpcs().isEmpty()) {
                return null;
            }

            var vpc = response.vpcs().get(0);
            return mapVpcToResource(vpc);

        } catch (Ec2Exception e) {
            if (e.awsErrorDetails().errorCode().equals("InvalidVpcID.NotFound")) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public VpcResource update(VpcResource resource) {
        log.info("Updating VPC: {}", resource.getVpcId());

        // Read current state
        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("VPC not found: " + resource.getVpcId());
        }

        // Update DNS settings if changed
        configureDnsSettings(resource.getVpcId(), resource);

        // Update tags
        if (resource.getTags() != null) {
            // Remove existing tags and apply new ones
            deleteAllTags(resource.getVpcId());
            if (!resource.getTags().isEmpty()) {
                applyTags(resource.getVpcId(), resource.getTags());
            }
        }

        return read(resource);
    }

    @Override
    public boolean delete(VpcResource resource) {
        if (resource.getVpcId() == null) {
            log.warn("Cannot delete VPC without vpcId");
            return false;
        }

        log.info("Deleting VPC: {}", resource.getVpcId());

        try {
            getClient().deleteVpc(DeleteVpcRequest.builder()
                    .vpcId(resource.getVpcId())
                    .build());

            log.info("Deleted VPC: {}", resource.getVpcId());
            return true;

        } catch (Ec2Exception e) {
            if (e.awsErrorDetails().errorCode().equals("InvalidVpcID.NotFound")) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public List<Diagnostic> validate(VpcResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getCidrBlock() == null || resource.getCidrBlock().isBlank()) {
            diagnostics.add(Diagnostic.error("cidr_block is required")
                    .withProperty("cidr_block"));
        } else {
            // Basic CIDR validation
            if (!resource.getCidrBlock().matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}/\\d{1,2}$")) {
                diagnostics.add(Diagnostic.error("Invalid CIDR block format",
                        "Expected format: x.x.x.x/x (e.g., 10.0.0.0/16)")
                        .withProperty("cidr_block"));
            }
        }

        if (resource.getInstanceTenancy() != null) {
            if (!resource.getInstanceTenancy().equals("default") &&
                !resource.getInstanceTenancy().equals("dedicated")) {
                diagnostics.add(Diagnostic.error("Invalid instance_tenancy",
                        "Must be 'default' or 'dedicated'")
                        .withProperty("instance_tenancy"));
            }
        }

        return diagnostics;
    }

    private void waitForVpcAvailable(String vpcId) {
        log.debug("Waiting for VPC {} to be available", vpcId);

        int maxAttempts = 40;
        int attempt = 0;

        while (attempt < maxAttempts) {
            var response = getClient().describeVpcs(DescribeVpcsRequest.builder()
                    .vpcIds(vpcId)
                    .build());

            if (!response.vpcs().isEmpty()) {
                var state = response.vpcs().get(0).state();
                if (state == VpcState.AVAILABLE) {
                    log.debug("VPC {} is now available", vpcId);
                    return;
                }
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for VPC", e);
            }
            attempt++;
        }

        throw new RuntimeException("Timeout waiting for VPC to become available: " + vpcId);
    }

    private void configureDnsSettings(String vpcId, VpcResource resource) {
        if (resource.getEnableDnsSupport() != null) {
            getClient().modifyVpcAttribute(ModifyVpcAttributeRequest.builder()
                    .vpcId(vpcId)
                    .enableDnsSupport(AttributeBooleanValue.builder()
                            .value(resource.getEnableDnsSupport())
                            .build())
                    .build());
        }

        if (resource.getEnableDnsHostnames() != null) {
            getClient().modifyVpcAttribute(ModifyVpcAttributeRequest.builder()
                    .vpcId(vpcId)
                    .enableDnsHostnames(AttributeBooleanValue.builder()
                            .value(resource.getEnableDnsHostnames())
                            .build())
                    .build());
        }
    }

    private void applyTags(String vpcId, java.util.Map<String, String> tags) {
        var tagList = tags.entrySet().stream()
                .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                .collect(Collectors.toList());

        getClient().createTags(CreateTagsRequest.builder()
                .resources(vpcId)
                .tags(tagList)
                .build());
    }

    private void deleteAllTags(String vpcId) {
        var response = getClient().describeVpcs(DescribeVpcsRequest.builder()
                .vpcIds(vpcId)
                .build());

        if (!response.vpcs().isEmpty()) {
            var existingTags = response.vpcs().get(0).tags();
            if (!existingTags.isEmpty()) {
                getClient().deleteTags(DeleteTagsRequest.builder()
                        .resources(vpcId)
                        .tags(existingTags)
                        .build());
            }
        }
    }

    private VpcResource mapVpcToResource(Vpc vpc) {
        var resource = new VpcResource();

        // Input properties
        resource.setCidrBlock(vpc.cidrBlock());
        resource.setInstanceTenancy(vpc.instanceTenancyAsString());

        // DNS settings (need to query separately)
        var dnsSupport = getClient().describeVpcAttribute(DescribeVpcAttributeRequest.builder()
                .vpcId(vpc.vpcId())
                .attribute(VpcAttributeName.ENABLE_DNS_SUPPORT)
                .build());
        resource.setEnableDnsSupport(dnsSupport.enableDnsSupport().value());

        var dnsHostnames = getClient().describeVpcAttribute(DescribeVpcAttributeRequest.builder()
                .vpcId(vpc.vpcId())
                .attribute(VpcAttributeName.ENABLE_DNS_HOSTNAMES)
                .build());
        resource.setEnableDnsHostnames(dnsHostnames.enableDnsHostnames().value());

        // Tags
        if (vpc.tags() != null && !vpc.tags().isEmpty()) {
            resource.setTags(vpc.tags().stream()
                    .collect(Collectors.toMap(Tag::key, Tag::value)));
        }

        // Cloud-managed properties
        resource.setVpcId(vpc.vpcId());
        resource.setState(vpc.stateAsString());
        resource.setOwnerId(vpc.ownerId());

        // Get additional VPC details
        var routeTables = getClient().describeRouteTables(DescribeRouteTablesRequest.builder()
                .filters(Filter.builder()
                        .name("vpc-id")
                        .values(vpc.vpcId())
                        .build(),
                        Filter.builder()
                                .name("association.main")
                                .values("true")
                                .build())
                .build());

        if (!routeTables.routeTables().isEmpty()) {
            resource.setMainRouteTableId(routeTables.routeTables().get(0).routeTableId());
        }

        // Get default network ACL
        var networkAcls = getClient().describeNetworkAcls(DescribeNetworkAclsRequest.builder()
                .filters(Filter.builder()
                        .name("vpc-id")
                        .values(vpc.vpcId())
                        .build(),
                        Filter.builder()
                                .name("default")
                                .values("true")
                                .build())
                .build());

        if (!networkAcls.networkAcls().isEmpty()) {
            resource.setDefaultNetworkAclId(networkAcls.networkAcls().get(0).networkAclId());
        }

        // Get default security group
        var securityGroups = getClient().describeSecurityGroups(DescribeSecurityGroupsRequest.builder()
                .filters(Filter.builder()
                        .name("vpc-id")
                        .values(vpc.vpcId())
                        .build(),
                        Filter.builder()
                                .name("group-name")
                                .values("default")
                                .build())
                .build());

        if (!securityGroups.securityGroups().isEmpty()) {
            resource.setDefaultSecurityGroupId(securityGroups.securityGroups().get(0).groupId());
        }

        return resource;
    }
}
