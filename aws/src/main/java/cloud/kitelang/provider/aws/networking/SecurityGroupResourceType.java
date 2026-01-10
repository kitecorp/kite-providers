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
 * ResourceTypeHandler for AWS Security Group.
 * Implements CRUD operations for Security Groups using AWS EC2 SDK.
 */
@Slf4j
public class SecurityGroupResourceType extends ResourceTypeHandler<SecurityGroupResource> {

    private volatile Ec2Client ec2Client;

    public SecurityGroupResourceType() {
        // Client created lazily to pick up configuration
    }

    public SecurityGroupResourceType(Ec2Client ec2Client) {
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
    public SecurityGroupResource create(SecurityGroupResource resource) {
        log.info("Creating Security Group '{}' in VPC '{}'",
                resource.getName(), resource.getVpcId());

        // Create the security group
        var createRequest = CreateSecurityGroupRequest.builder()
                .groupName(resource.getName())
                .description(resource.getDescription())
                .vpcId(resource.getVpcId())
                .build();

        var createResponse = getClient().createSecurityGroup(createRequest);
        String groupId = createResponse.groupId();
        log.info("Created Security Group: {}", groupId);

        resource.setSecurityGroupId(groupId);

        // Apply tags
        if (resource.getTags() != null && !resource.getTags().isEmpty()) {
            applyTags(groupId, resource.getTags());
        }

        // Add ingress rules
        if (resource.getIngress() != null && !resource.getIngress().isEmpty()) {
            addIngressRules(groupId, resource.getIngress());
        }

        // Add egress rules (replace default allow-all)
        if (resource.getEgress() != null && !resource.getEgress().isEmpty()) {
            // First revoke the default egress rule
            revokeDefaultEgressRule(groupId);
            // Then add custom egress rules
            addEgressRules(groupId, resource.getEgress());
        }

        // Refresh to get all cloud-managed properties
        return read(resource);
    }

    private void revokeDefaultEgressRule(String groupId) {
        try {
            // Default egress rule allows all outbound traffic
            var revokeRequest = RevokeSecurityGroupEgressRequest.builder()
                    .groupId(groupId)
                    .ipPermissions(IpPermission.builder()
                            .ipProtocol("-1")
                            .ipRanges(IpRange.builder().cidrIp("0.0.0.0/0").build())
                            .build())
                    .build();
            getClient().revokeSecurityGroupEgress(revokeRequest);
        } catch (Ec2Exception e) {
            // Ignore if rule doesn't exist
            log.debug("Could not revoke default egress rule: {}", e.getMessage());
        }
    }

    private void addIngressRules(String groupId, List<SecurityGroupRule> rules) {
        if (rules == null || rules.isEmpty()) return;

        var ipPermissions = rules.stream()
                .map(this::toIpPermission)
                .collect(Collectors.toList());

        var request = AuthorizeSecurityGroupIngressRequest.builder()
                .groupId(groupId)
                .ipPermissions(ipPermissions)
                .build();

        getClient().authorizeSecurityGroupIngress(request);
        log.debug("Added {} ingress rules to {}", rules.size(), groupId);
    }

    private void addEgressRules(String groupId, List<SecurityGroupRule> rules) {
        if (rules == null || rules.isEmpty()) return;

        var ipPermissions = rules.stream()
                .map(this::toIpPermission)
                .collect(Collectors.toList());

        var request = AuthorizeSecurityGroupEgressRequest.builder()
                .groupId(groupId)
                .ipPermissions(ipPermissions)
                .build();

        getClient().authorizeSecurityGroupEgress(request);
        log.debug("Added {} egress rules to {}", rules.size(), groupId);
    }

    private IpPermission toIpPermission(SecurityGroupRule rule) {
        var builder = IpPermission.builder()
                .ipProtocol(rule.getProtocol())
                .fromPort(rule.getFromPort())
                .toPort(rule.getToPort());

        // Add IPv4 CIDR blocks
        if (rule.getCidrBlocks() != null && !rule.getCidrBlocks().isEmpty()) {
            var ipRanges = rule.getCidrBlocks().stream()
                    .map(cidr -> IpRange.builder()
                            .cidrIp(cidr)
                            .description(rule.getDescription())
                            .build())
                    .collect(Collectors.toList());
            builder.ipRanges(ipRanges);
        }

        // Add IPv6 CIDR blocks
        if (rule.getIpv6CidrBlocks() != null && !rule.getIpv6CidrBlocks().isEmpty()) {
            var ipv6Ranges = rule.getIpv6CidrBlocks().stream()
                    .map(cidr -> Ipv6Range.builder()
                            .cidrIpv6(cidr)
                            .description(rule.getDescription())
                            .build())
                    .collect(Collectors.toList());
            builder.ipv6Ranges(ipv6Ranges);
        }

        // Add security group references
        if (rule.getSecurityGroupIds() != null && !rule.getSecurityGroupIds().isEmpty()) {
            var userIdGroupPairs = rule.getSecurityGroupIds().stream()
                    .map(sgId -> UserIdGroupPair.builder()
                            .groupId(sgId)
                            .description(rule.getDescription())
                            .build())
                    .collect(Collectors.toList());
            builder.userIdGroupPairs(userIdGroupPairs);
        }

        return builder.build();
    }

    private void applyTags(String groupId, java.util.Map<String, String> tags) {
        var tagList = tags.entrySet().stream()
                .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                .collect(Collectors.toList());

        getClient().createTags(CreateTagsRequest.builder()
                .resources(groupId)
                .tags(tagList)
                .build());
    }

    @Override
    public SecurityGroupResource read(SecurityGroupResource resource) {
        if (resource.getSecurityGroupId() == null) {
            log.warn("Cannot read Security Group without securityGroupId");
            return null;
        }

        log.info("Reading Security Group: {}", resource.getSecurityGroupId());

        try {
            var response = getClient().describeSecurityGroups(DescribeSecurityGroupsRequest.builder()
                    .groupIds(resource.getSecurityGroupId())
                    .build());

            if (response.securityGroups().isEmpty()) {
                return null;
            }

            return mapToResource(response.securityGroups().get(0));

        } catch (Ec2Exception e) {
            if (e.awsErrorDetails().errorCode().equals("InvalidGroup.NotFound")) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public SecurityGroupResource update(SecurityGroupResource resource) {
        log.info("Updating Security Group: {}", resource.getSecurityGroupId());

        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("Security Group not found: " + resource.getSecurityGroupId());
        }

        String groupId = resource.getSecurityGroupId();

        // Update ingress rules - revoke old, add new
        if (current.getIngress() != null && !current.getIngress().isEmpty()) {
            revokeIngressRules(groupId, current.getIngress());
        }
        if (resource.getIngress() != null && !resource.getIngress().isEmpty()) {
            addIngressRules(groupId, resource.getIngress());
        }

        // Update egress rules - revoke old, add new
        if (current.getEgress() != null && !current.getEgress().isEmpty()) {
            revokeEgressRules(groupId, current.getEgress());
        }
        if (resource.getEgress() != null && !resource.getEgress().isEmpty()) {
            addEgressRules(groupId, resource.getEgress());
        }

        // Update tags
        if (resource.getTags() != null) {
            // Delete existing tags
            if (current.getTags() != null && !current.getTags().isEmpty()) {
                var oldTags = current.getTags().entrySet().stream()
                        .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                        .collect(Collectors.toList());
                getClient().deleteTags(DeleteTagsRequest.builder()
                        .resources(groupId)
                        .tags(oldTags)
                        .build());
            }
            // Apply new tags
            if (!resource.getTags().isEmpty()) {
                applyTags(groupId, resource.getTags());
            }
        }

        return read(resource);
    }

    private void revokeIngressRules(String groupId, List<SecurityGroupRule> rules) {
        if (rules == null || rules.isEmpty()) return;

        var ipPermissions = rules.stream()
                .map(this::toIpPermission)
                .collect(Collectors.toList());

        try {
            getClient().revokeSecurityGroupIngress(RevokeSecurityGroupIngressRequest.builder()
                    .groupId(groupId)
                    .ipPermissions(ipPermissions)
                    .build());
        } catch (Ec2Exception e) {
            log.debug("Could not revoke ingress rules: {}", e.getMessage());
        }
    }

    private void revokeEgressRules(String groupId, List<SecurityGroupRule> rules) {
        if (rules == null || rules.isEmpty()) return;

        var ipPermissions = rules.stream()
                .map(this::toIpPermission)
                .collect(Collectors.toList());

        try {
            getClient().revokeSecurityGroupEgress(RevokeSecurityGroupEgressRequest.builder()
                    .groupId(groupId)
                    .ipPermissions(ipPermissions)
                    .build());
        } catch (Ec2Exception e) {
            log.debug("Could not revoke egress rules: {}", e.getMessage());
        }
    }

    @Override
    public boolean delete(SecurityGroupResource resource) {
        if (resource.getSecurityGroupId() == null) {
            log.warn("Cannot delete Security Group without securityGroupId");
            return false;
        }

        log.info("Deleting Security Group: {}", resource.getSecurityGroupId());

        try {
            getClient().deleteSecurityGroup(DeleteSecurityGroupRequest.builder()
                    .groupId(resource.getSecurityGroupId())
                    .build());

            log.info("Deleted Security Group: {}", resource.getSecurityGroupId());
            return true;

        } catch (Ec2Exception e) {
            if (e.awsErrorDetails().errorCode().equals("InvalidGroup.NotFound")) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public List<Diagnostic> validate(SecurityGroupResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getName() == null || resource.getName().isBlank()) {
            diagnostics.add(Diagnostic.error("name is required")
                    .withProperty("name"));
        } else {
            if (resource.getName().length() > 255) {
                diagnostics.add(Diagnostic.error("name must be 255 characters or less")
                        .withProperty("name"));
            }
        }

        if (resource.getDescription() == null || resource.getDescription().isBlank()) {
            diagnostics.add(Diagnostic.error("description is required")
                    .withProperty("description"));
        } else {
            if (resource.getDescription().length() > 255) {
                diagnostics.add(Diagnostic.error("description must be 255 characters or less")
                        .withProperty("description"));
            }
        }

        if (resource.getVpcId() == null || resource.getVpcId().isBlank()) {
            diagnostics.add(Diagnostic.error("vpcId is required")
                    .withProperty("vpcId"));
        }

        // Validate ingress rules
        if (resource.getIngress() != null) {
            for (int i = 0; i < resource.getIngress().size(); i++) {
                validateRule(resource.getIngress().get(i), "ingress[" + i + "]", diagnostics);
            }
        }

        // Validate egress rules
        if (resource.getEgress() != null) {
            for (int i = 0; i < resource.getEgress().size(); i++) {
                validateRule(resource.getEgress().get(i), "egress[" + i + "]", diagnostics);
            }
        }

        return diagnostics;
    }

    private void validateRule(SecurityGroupRule rule, String prefix, List<Diagnostic> diagnostics) {
        if (rule.getProtocol() == null || rule.getProtocol().isBlank()) {
            diagnostics.add(Diagnostic.error("protocol is required")
                    .withProperty(prefix + ".protocol"));
        }

        if (rule.getFromPort() == null) {
            diagnostics.add(Diagnostic.error("fromPort is required")
                    .withProperty(prefix + ".fromPort"));
        }

        if (rule.getToPort() == null) {
            diagnostics.add(Diagnostic.error("toPort is required")
                    .withProperty(prefix + ".toPort"));
        }

        // Must have at least one source/destination
        boolean hasCidr = rule.getCidrBlocks() != null && !rule.getCidrBlocks().isEmpty();
        boolean hasIpv6 = rule.getIpv6CidrBlocks() != null && !rule.getIpv6CidrBlocks().isEmpty();
        boolean hasSg = rule.getSecurityGroupIds() != null && !rule.getSecurityGroupIds().isEmpty();

        if (!hasCidr && !hasIpv6 && !hasSg) {
            diagnostics.add(Diagnostic.error("rule must have cidrBlocks, ipv6CidrBlocks, or securityGroupIds")
                    .withProperty(prefix));
        }
    }

    private SecurityGroupResource mapToResource(SecurityGroup sg) {
        var resource = new SecurityGroupResource();

        // Input properties
        resource.setName(sg.groupName());
        resource.setDescription(sg.description());
        resource.setVpcId(sg.vpcId());

        // Map ingress rules
        if (sg.ipPermissions() != null && !sg.ipPermissions().isEmpty()) {
            var ingress = sg.ipPermissions().stream()
                    .flatMap(perm -> mapIpPermissionToRules(perm).stream())
                    .collect(Collectors.toList());
            resource.setIngress(ingress);
        }

        // Map egress rules
        if (sg.ipPermissionsEgress() != null && !sg.ipPermissionsEgress().isEmpty()) {
            var egress = sg.ipPermissionsEgress().stream()
                    .flatMap(perm -> mapIpPermissionToRules(perm).stream())
                    .collect(Collectors.toList());
            resource.setEgress(egress);
        }

        // Tags
        if (sg.tags() != null && !sg.tags().isEmpty()) {
            resource.setTags(sg.tags().stream()
                    .collect(Collectors.toMap(Tag::key, Tag::value)));
        }

        // Cloud-managed properties
        resource.setSecurityGroupId(sg.groupId());
        resource.setOwnerId(sg.ownerId());

        return resource;
    }

    private List<SecurityGroupRule> mapIpPermissionToRules(IpPermission perm) {
        var rules = new ArrayList<SecurityGroupRule>();

        // Create rule for IPv4 CIDR blocks
        if (perm.ipRanges() != null && !perm.ipRanges().isEmpty()) {
            var rule = SecurityGroupRule.builder()
                    .protocol(perm.ipProtocol())
                    .fromPort(perm.fromPort())
                    .toPort(perm.toPort())
                    .cidrBlocks(perm.ipRanges().stream()
                            .map(IpRange::cidrIp)
                            .collect(Collectors.toList()))
                    .description(perm.ipRanges().get(0).description())
                    .build();
            rules.add(rule);
        }

        // Create rule for IPv6 CIDR blocks
        if (perm.ipv6Ranges() != null && !perm.ipv6Ranges().isEmpty()) {
            var rule = SecurityGroupRule.builder()
                    .protocol(perm.ipProtocol())
                    .fromPort(perm.fromPort())
                    .toPort(perm.toPort())
                    .ipv6CidrBlocks(perm.ipv6Ranges().stream()
                            .map(Ipv6Range::cidrIpv6)
                            .collect(Collectors.toList()))
                    .description(perm.ipv6Ranges().get(0).description())
                    .build();
            rules.add(rule);
        }

        // Create rule for security group references
        if (perm.userIdGroupPairs() != null && !perm.userIdGroupPairs().isEmpty()) {
            var rule = SecurityGroupRule.builder()
                    .protocol(perm.ipProtocol())
                    .fromPort(perm.fromPort())
                    .toPort(perm.toPort())
                    .securityGroupIds(perm.userIdGroupPairs().stream()
                            .map(UserIdGroupPair::groupId)
                            .collect(Collectors.toList()))
                    .description(perm.userIdGroupPairs().get(0).description())
                    .build();
            rules.add(rule);
        }

        return rules;
    }
}
