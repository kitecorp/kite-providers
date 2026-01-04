package cloud.kitelang.provider.aws.iam;

import cloud.kitelang.provider.Diagnostic;
import cloud.kitelang.provider.ResourceTypeHandler;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ResourceTypeHandler for AWS IAM Policy.
 * Implements CRUD operations for IAM policies using AWS SDK.
 */
@Slf4j
public class IamPolicyResourceType extends ResourceTypeHandler<IamPolicyResource> {

    private final IamClient iamClient;

    public IamPolicyResourceType() {
        this.iamClient = IamClient.builder().build();
    }

    public IamPolicyResourceType(IamClient iamClient) {
        this.iamClient = iamClient;
    }

    @Override
    public IamPolicyResource create(IamPolicyResource resource) {
        log.info("Creating IAM Policy '{}'", resource.getName());

        var requestBuilder = CreatePolicyRequest.builder()
                .policyName(resource.getName())
                .policyDocument(resource.getPolicy());

        if (resource.getDescription() != null) {
            requestBuilder.description(resource.getDescription());
        }
        if (resource.getPath() != null) {
            requestBuilder.path(resource.getPath());
        }
        if (resource.getTags() != null && !resource.getTags().isEmpty()) {
            requestBuilder.tags(toIamTags(resource.getTags()));
        }

        var response = iamClient.createPolicy(requestBuilder.build());
        log.info("Created IAM Policy: {} (ARN: {})",
                response.policy().policyName(), response.policy().arn());

        resource.setArn(response.policy().arn());
        return read(resource);
    }

    @Override
    public IamPolicyResource read(IamPolicyResource resource) {
        String arn = resource.getArn();
        if (arn == null && resource.getName() != null) {
            // Try to find by name - need to list and filter
            arn = findPolicyArnByName(resource.getName(), resource.getPath());
        }

        if (arn == null) {
            log.warn("Cannot read IAM Policy without arn or name");
            return null;
        }

        log.info("Reading IAM Policy: {}", arn);

        try {
            var response = iamClient.getPolicy(GetPolicyRequest.builder()
                    .policyArn(arn)
                    .build());

            return mapToResource(response.policy());

        } catch (NoSuchEntityException e) {
            return null;
        }
    }

    private String findPolicyArnByName(String name, String path) {
        String scope = "Local"; // Only search customer-managed policies
        var request = ListPoliciesRequest.builder()
                .scope(PolicyScopeType.fromValue(scope))
                .pathPrefix(path != null ? path : "/")
                .build();

        var response = iamClient.listPolicies(request);
        for (var policy : response.policies()) {
            if (policy.policyName().equals(name)) {
                return policy.arn();
            }
        }
        return null;
    }

    @Override
    public IamPolicyResource update(IamPolicyResource resource) {
        log.info("Updating IAM Policy: {}", resource.getArn());

        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("IAM Policy not found: " + resource.getArn());
        }

        String arn = current.getArn();

        // Update policy document by creating a new version
        if (resource.getPolicy() != null) {
            // Delete old versions if at limit (max 5 versions)
            deleteOldPolicyVersions(arn);

            // Create new version
            iamClient.createPolicyVersion(CreatePolicyVersionRequest.builder()
                    .policyArn(arn)
                    .policyDocument(resource.getPolicy())
                    .setAsDefault(true)
                    .build());
            log.debug("Created new policy version for {}", arn);
        }

        // Update tags
        if (resource.getTags() != null) {
            // Untag all existing tags
            if (current.getTags() != null && !current.getTags().isEmpty()) {
                iamClient.untagPolicy(UntagPolicyRequest.builder()
                        .policyArn(arn)
                        .tagKeys(current.getTags().keySet().stream().toList())
                        .build());
            }
            // Apply new tags
            if (!resource.getTags().isEmpty()) {
                iamClient.tagPolicy(TagPolicyRequest.builder()
                        .policyArn(arn)
                        .tags(toIamTags(resource.getTags()))
                        .build());
            }
        }

        return read(resource);
    }

    private void deleteOldPolicyVersions(String policyArn) {
        var versions = iamClient.listPolicyVersions(ListPolicyVersionsRequest.builder()
                .policyArn(policyArn)
                .build()).versions();

        // Keep only the default version if we're at the limit
        if (versions.size() >= 5) {
            for (var version : versions) {
                if (!version.isDefaultVersion()) {
                    iamClient.deletePolicyVersion(DeletePolicyVersionRequest.builder()
                            .policyArn(policyArn)
                            .versionId(version.versionId())
                            .build());
                    log.debug("Deleted old policy version {} for {}", version.versionId(), policyArn);
                    break; // Only need to delete one to make room
                }
            }
        }
    }

    @Override
    public boolean delete(IamPolicyResource resource) {
        String arn = resource.getArn();
        if (arn == null && resource.getName() != null) {
            arn = findPolicyArnByName(resource.getName(), resource.getPath());
        }

        if (arn == null) {
            log.warn("Cannot delete IAM Policy without arn");
            return false;
        }

        log.info("Deleting IAM Policy: {}", arn);

        try {
            // Detach from all entities first
            detachPolicyFromAllEntities(arn);

            // Delete all non-default versions
            var versions = iamClient.listPolicyVersions(ListPolicyVersionsRequest.builder()
                    .policyArn(arn)
                    .build()).versions();
            for (var version : versions) {
                if (!version.isDefaultVersion()) {
                    iamClient.deletePolicyVersion(DeletePolicyVersionRequest.builder()
                            .policyArn(arn)
                            .versionId(version.versionId())
                            .build());
                }
            }

            // Delete the policy
            iamClient.deletePolicy(DeletePolicyRequest.builder()
                    .policyArn(arn)
                    .build());

            log.info("Deleted IAM Policy: {}", arn);
            return true;

        } catch (NoSuchEntityException e) {
            return false;
        }
    }

    private void detachPolicyFromAllEntities(String policyArn) {
        // Detach from roles
        var roles = iamClient.listEntitiesForPolicy(ListEntitiesForPolicyRequest.builder()
                .policyArn(policyArn)
                .entityFilter(EntityType.ROLE)
                .build());
        for (var role : roles.policyRoles()) {
            iamClient.detachRolePolicy(DetachRolePolicyRequest.builder()
                    .roleName(role.roleName())
                    .policyArn(policyArn)
                    .build());
        }

        // Detach from users
        var users = iamClient.listEntitiesForPolicy(ListEntitiesForPolicyRequest.builder()
                .policyArn(policyArn)
                .entityFilter(EntityType.USER)
                .build());
        for (var user : users.policyUsers()) {
            iamClient.detachUserPolicy(DetachUserPolicyRequest.builder()
                    .userName(user.userName())
                    .policyArn(policyArn)
                    .build());
        }

        // Detach from groups
        var groups = iamClient.listEntitiesForPolicy(ListEntitiesForPolicyRequest.builder()
                .policyArn(policyArn)
                .entityFilter(EntityType.GROUP)
                .build());
        for (var group : groups.policyGroups()) {
            iamClient.detachGroupPolicy(DetachGroupPolicyRequest.builder()
                    .groupName(group.groupName())
                    .policyArn(policyArn)
                    .build());
        }
    }

    @Override
    public List<Diagnostic> validate(IamPolicyResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getName() == null || resource.getName().isBlank()) {
            diagnostics.add(Diagnostic.error("name is required")
                    .withProperty("name"));
        } else {
            if (resource.getName().length() > 128) {
                diagnostics.add(Diagnostic.error("name must be 128 characters or less")
                        .withProperty("name"));
            }
            if (!resource.getName().matches("[\\w+=,.@-]+")) {
                diagnostics.add(Diagnostic.error("name contains invalid characters")
                        .withProperty("name"));
            }
        }

        if (resource.getPolicy() == null || resource.getPolicy().isBlank()) {
            diagnostics.add(Diagnostic.error("policy document is required")
                    .withProperty("policy"));
        }

        if (resource.getPath() != null && !resource.getPath().matches("(/[\\w+=,.@-]+)+/|/")) {
            diagnostics.add(Diagnostic.error("path must start and end with / and contain valid characters")
                    .withProperty("path"));
        }

        return diagnostics;
    }

    private List<Tag> toIamTags(Map<String, String> tags) {
        return tags.entrySet().stream()
                .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                .collect(Collectors.toList());
    }

    private IamPolicyResource mapToResource(Policy policy) {
        var resource = new IamPolicyResource();

        // Input properties
        resource.setName(policy.policyName());
        resource.setDescription(policy.description());
        resource.setPath(policy.path());

        // Get the policy document from default version
        if (policy.defaultVersionId() != null) {
            var versionResponse = iamClient.getPolicyVersion(GetPolicyVersionRequest.builder()
                    .policyArn(policy.arn())
                    .versionId(policy.defaultVersionId())
                    .build());
            resource.setPolicy(versionResponse.policyVersion().document());
        }

        // Tags
        if (policy.tags() != null && !policy.tags().isEmpty()) {
            resource.setTags(policy.tags().stream()
                    .collect(Collectors.toMap(Tag::key, Tag::value)));
        }

        // Cloud-managed properties
        resource.setPolicyId(policy.policyId());
        resource.setArn(policy.arn());
        resource.setCreateDate(policy.createDate() != null ? policy.createDate().toString() : null);
        resource.setAttachmentCount(policy.attachmentCount());
        resource.setDefaultVersionId(policy.defaultVersionId());

        return resource;
    }
}
