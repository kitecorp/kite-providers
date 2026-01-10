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
 * ResourceTypeHandler for AWS IAM Role.
 * Implements CRUD operations for IAM roles using AWS SDK.
 */
@Slf4j
public class IamRoleResourceType extends ResourceTypeHandler<IamRoleResource> {

    private volatile IamClient iamClient;

    public IamRoleResourceType() {
        // Client created lazily to pick up configuration
    }

    public IamRoleResourceType(IamClient iamClient) {
        this.iamClient = iamClient;
    }

    /**
     * Get or create an IAM client.
     * Creates the client lazily to allow provider configuration to be applied first.
     */
    private IamClient getClient() {
        if (iamClient == null) {
            synchronized (this) {
                if (iamClient == null) {
                    log.debug("Creating IAM client with current AWS configuration");
                    iamClient = IamClient.builder().build();
                }
            }
        }
        return iamClient;
    }

    @Override
    public IamRoleResource create(IamRoleResource resource) {
        log.info("Creating IAM Role '{}'", resource.getName());

        var requestBuilder = CreateRoleRequest.builder()
                .roleName(resource.getName())
                .assumeRolePolicyDocument(resource.getAssumeRolePolicy());

        if (resource.getDescription() != null) {
            requestBuilder.description(resource.getDescription());
        }
        if (resource.getPath() != null) {
            requestBuilder.path(resource.getPath());
        }
        if (resource.getMaxSessionDuration() != null) {
            requestBuilder.maxSessionDuration(resource.getMaxSessionDuration());
        }
        if (resource.getTags() != null && !resource.getTags().isEmpty()) {
            requestBuilder.tags(toIamTags(resource.getTags()));
        }

        var response = getClient().createRole(requestBuilder.build());
        log.info("Created IAM Role: {} (ARN: {})",
                response.role().roleName(), response.role().arn());

        // Attach managed policies
        if (resource.getManagedPolicyArns() != null) {
            for (var policyArn : resource.getManagedPolicyArns()) {
                getClient().attachRolePolicy(AttachRolePolicyRequest.builder()
                        .roleName(resource.getName())
                        .policyArn(policyArn)
                        .build());
                log.debug("Attached managed policy {} to role {}", policyArn, resource.getName());
            }
        }

        // Add inline policies
        if (resource.getInlinePolicies() != null) {
            for (var entry : resource.getInlinePolicies().entrySet()) {
                getClient().putRolePolicy(PutRolePolicyRequest.builder()
                        .roleName(resource.getName())
                        .policyName(entry.getKey())
                        .policyDocument(entry.getValue())
                        .build());
                log.debug("Added inline policy {} to role {}", entry.getKey(), resource.getName());
            }
        }

        return read(resource);
    }

    @Override
    public IamRoleResource read(IamRoleResource resource) {
        if (resource.getName() == null) {
            log.warn("Cannot read IAM Role without name");
            return null;
        }

        log.info("Reading IAM Role: {}", resource.getName());

        try {
            var response = getClient().getRole(GetRoleRequest.builder()
                    .roleName(resource.getName())
                    .build());

            return mapToResource(response.role());

        } catch (NoSuchEntityException e) {
            return null;
        }
    }

    @Override
    public IamRoleResource update(IamRoleResource resource) {
        log.info("Updating IAM Role: {}", resource.getName());

        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("IAM Role not found: " + resource.getName());
        }

        // Update assume role policy
        if (resource.getAssumeRolePolicy() != null) {
            getClient().updateAssumeRolePolicy(UpdateAssumeRolePolicyRequest.builder()
                    .roleName(resource.getName())
                    .policyDocument(resource.getAssumeRolePolicy())
                    .build());
        }

        // Update description
        if (resource.getDescription() != null) {
            getClient().updateRole(UpdateRoleRequest.builder()
                    .roleName(resource.getName())
                    .description(resource.getDescription())
                    .maxSessionDuration(resource.getMaxSessionDuration())
                    .build());
        }

        // Update managed policies - detach old, attach new
        var currentPolicies = listAttachedPolicies(resource.getName());
        var desiredPolicies = resource.getManagedPolicyArns() != null ?
                resource.getManagedPolicyArns() : List.<String>of();

        // Detach policies not in desired list
        for (var policyArn : currentPolicies) {
            if (!desiredPolicies.contains(policyArn)) {
                getClient().detachRolePolicy(DetachRolePolicyRequest.builder()
                        .roleName(resource.getName())
                        .policyArn(policyArn)
                        .build());
                log.debug("Detached policy {} from role {}", policyArn, resource.getName());
            }
        }

        // Attach new policies
        for (var policyArn : desiredPolicies) {
            if (!currentPolicies.contains(policyArn)) {
                getClient().attachRolePolicy(AttachRolePolicyRequest.builder()
                        .roleName(resource.getName())
                        .policyArn(policyArn)
                        .build());
                log.debug("Attached policy {} to role {}", policyArn, resource.getName());
            }
        }

        // Update inline policies
        if (resource.getInlinePolicies() != null) {
            // Delete old inline policies
            var currentInline = listInlinePolicies(resource.getName());
            for (var policyName : currentInline) {
                if (!resource.getInlinePolicies().containsKey(policyName)) {
                    getClient().deleteRolePolicy(DeleteRolePolicyRequest.builder()
                            .roleName(resource.getName())
                            .policyName(policyName)
                            .build());
                }
            }
            // Add/update inline policies
            for (var entry : resource.getInlinePolicies().entrySet()) {
                getClient().putRolePolicy(PutRolePolicyRequest.builder()
                        .roleName(resource.getName())
                        .policyName(entry.getKey())
                        .policyDocument(entry.getValue())
                        .build());
            }
        }

        // Update tags
        if (resource.getTags() != null) {
            // Untag all existing tags
            if (current.getTags() != null && !current.getTags().isEmpty()) {
                getClient().untagRole(UntagRoleRequest.builder()
                        .roleName(resource.getName())
                        .tagKeys(current.getTags().keySet().stream().toList())
                        .build());
            }
            // Apply new tags
            if (!resource.getTags().isEmpty()) {
                getClient().tagRole(TagRoleRequest.builder()
                        .roleName(resource.getName())
                        .tags(toIamTags(resource.getTags()))
                        .build());
            }
        }

        return read(resource);
    }

    private List<String> listAttachedPolicies(String roleName) {
        var response = getClient().listAttachedRolePolicies(ListAttachedRolePoliciesRequest.builder()
                .roleName(roleName)
                .build());
        return response.attachedPolicies().stream()
                .map(AttachedPolicy::policyArn)
                .collect(Collectors.toList());
    }

    private List<String> listInlinePolicies(String roleName) {
        var response = getClient().listRolePolicies(ListRolePoliciesRequest.builder()
                .roleName(roleName)
                .build());
        return response.policyNames();
    }

    @Override
    public boolean delete(IamRoleResource resource) {
        if (resource.getName() == null) {
            log.warn("Cannot delete IAM Role without name");
            return false;
        }

        log.info("Deleting IAM Role: {}", resource.getName());

        try {
            // Detach all managed policies first
            var attachedPolicies = listAttachedPolicies(resource.getName());
            for (var policyArn : attachedPolicies) {
                getClient().detachRolePolicy(DetachRolePolicyRequest.builder()
                        .roleName(resource.getName())
                        .policyArn(policyArn)
                        .build());
            }

            // Delete all inline policies
            var inlinePolicies = listInlinePolicies(resource.getName());
            for (var policyName : inlinePolicies) {
                getClient().deleteRolePolicy(DeleteRolePolicyRequest.builder()
                        .roleName(resource.getName())
                        .policyName(policyName)
                        .build());
            }

            // Remove role from all instance profiles
            var profiles = getClient().listInstanceProfilesForRole(ListInstanceProfilesForRoleRequest.builder()
                    .roleName(resource.getName())
                    .build());
            for (var profile : profiles.instanceProfiles()) {
                getClient().removeRoleFromInstanceProfile(RemoveRoleFromInstanceProfileRequest.builder()
                        .instanceProfileName(profile.instanceProfileName())
                        .roleName(resource.getName())
                        .build());
            }

            // Delete the role
            getClient().deleteRole(DeleteRoleRequest.builder()
                    .roleName(resource.getName())
                    .build());

            log.info("Deleted IAM Role: {}", resource.getName());
            return true;

        } catch (NoSuchEntityException e) {
            return false;
        }
    }

    @Override
    public List<Diagnostic> validate(IamRoleResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getName() == null || resource.getName().isBlank()) {
            diagnostics.add(Diagnostic.error("name is required")
                    .withProperty("name"));
        } else {
            if (resource.getName().length() > 64) {
                diagnostics.add(Diagnostic.error("name must be 64 characters or less")
                        .withProperty("name"));
            }
            if (!resource.getName().matches("[\\w+=,.@-]+")) {
                diagnostics.add(Diagnostic.error("name contains invalid characters")
                        .withProperty("name"));
            }
        }

        if (resource.getAssumeRolePolicy() == null || resource.getAssumeRolePolicy().isBlank()) {
            diagnostics.add(Diagnostic.error("assumeRolePolicy is required")
                    .withProperty("assumeRolePolicy"));
        }

        if (resource.getMaxSessionDuration() != null) {
            if (resource.getMaxSessionDuration() < 3600 || resource.getMaxSessionDuration() > 43200) {
                diagnostics.add(Diagnostic.error("maxSessionDuration must be between 3600 and 43200 seconds")
                        .withProperty("maxSessionDuration"));
            }
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

    private IamRoleResource mapToResource(Role role) {
        var resource = new IamRoleResource();

        // Input properties
        resource.setName(role.roleName());
        resource.setDescription(role.description());
        resource.setPath(role.path());
        resource.setAssumeRolePolicy(role.assumeRolePolicyDocument());
        resource.setMaxSessionDuration(role.maxSessionDuration());

        // Get attached policies
        resource.setManagedPolicyArns(listAttachedPolicies(role.roleName()));

        // Get inline policies
        var inlinePolicyNames = listInlinePolicies(role.roleName());
        if (!inlinePolicyNames.isEmpty()) {
            var inlinePolicies = new java.util.HashMap<String, String>();
            for (var policyName : inlinePolicyNames) {
                var policyResponse = getClient().getRolePolicy(GetRolePolicyRequest.builder()
                        .roleName(role.roleName())
                        .policyName(policyName)
                        .build());
                inlinePolicies.put(policyName, policyResponse.policyDocument());
            }
            resource.setInlinePolicies(inlinePolicies);
        }

        // Tags
        if (role.tags() != null && !role.tags().isEmpty()) {
            resource.setTags(role.tags().stream()
                    .collect(Collectors.toMap(Tag::key, Tag::value)));
        }

        // Cloud-managed properties
        resource.setRoleId(role.roleId());
        resource.setArn(role.arn());
        resource.setCreateDate(role.createDate() != null ? role.createDate().toString() : null);

        return resource;
    }
}
