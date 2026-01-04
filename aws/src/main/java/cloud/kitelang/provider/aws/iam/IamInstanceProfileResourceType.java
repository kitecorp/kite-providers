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
 * ResourceTypeHandler for AWS IAM Instance Profile.
 * Implements CRUD operations for IAM instance profiles using AWS SDK.
 */
@Slf4j
public class IamInstanceProfileResourceType extends ResourceTypeHandler<IamInstanceProfileResource> {

    private final IamClient iamClient;

    public IamInstanceProfileResourceType() {
        this.iamClient = IamClient.builder().build();
    }

    public IamInstanceProfileResourceType(IamClient iamClient) {
        this.iamClient = iamClient;
    }

    @Override
    public IamInstanceProfileResource create(IamInstanceProfileResource resource) {
        log.info("Creating IAM Instance Profile '{}'", resource.getName());

        var requestBuilder = CreateInstanceProfileRequest.builder()
                .instanceProfileName(resource.getName());

        if (resource.getPath() != null) {
            requestBuilder.path(resource.getPath());
        }
        if (resource.getTags() != null && !resource.getTags().isEmpty()) {
            requestBuilder.tags(toIamTags(resource.getTags()));
        }

        var response = iamClient.createInstanceProfile(requestBuilder.build());
        log.info("Created IAM Instance Profile: {} (ARN: {})",
                response.instanceProfile().instanceProfileName(),
                response.instanceProfile().arn());

        // Add role to instance profile if specified
        if (resource.getRole() != null && !resource.getRole().isBlank()) {
            // Wait a moment for the instance profile to be ready
            waitForInstanceProfile(resource.getName());

            iamClient.addRoleToInstanceProfile(AddRoleToInstanceProfileRequest.builder()
                    .instanceProfileName(resource.getName())
                    .roleName(resource.getRole())
                    .build());
            log.debug("Added role {} to instance profile {}", resource.getRole(), resource.getName());
        }

        return read(resource);
    }

    private void waitForInstanceProfile(String name) {
        int maxAttempts = 10;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                iamClient.getInstanceProfile(GetInstanceProfileRequest.builder()
                        .instanceProfileName(name)
                        .build());
                return;
            } catch (NoSuchEntityException e) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for instance profile", ie);
                }
            }
        }
    }

    @Override
    public IamInstanceProfileResource read(IamInstanceProfileResource resource) {
        if (resource.getName() == null) {
            log.warn("Cannot read IAM Instance Profile without name");
            return null;
        }

        log.info("Reading IAM Instance Profile: {}", resource.getName());

        try {
            var response = iamClient.getInstanceProfile(GetInstanceProfileRequest.builder()
                    .instanceProfileName(resource.getName())
                    .build());

            return mapToResource(response.instanceProfile());

        } catch (NoSuchEntityException e) {
            return null;
        }
    }

    @Override
    public IamInstanceProfileResource update(IamInstanceProfileResource resource) {
        log.info("Updating IAM Instance Profile: {}", resource.getName());

        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("IAM Instance Profile not found: " + resource.getName());
        }

        // Update role association
        String currentRole = current.getRole();
        String desiredRole = resource.getRole();

        if (currentRole != null && !currentRole.equals(desiredRole)) {
            // Remove current role
            iamClient.removeRoleFromInstanceProfile(RemoveRoleFromInstanceProfileRequest.builder()
                    .instanceProfileName(resource.getName())
                    .roleName(currentRole)
                    .build());
            log.debug("Removed role {} from instance profile {}", currentRole, resource.getName());
        }

        if (desiredRole != null && !desiredRole.isBlank() && !desiredRole.equals(currentRole)) {
            // Add new role
            iamClient.addRoleToInstanceProfile(AddRoleToInstanceProfileRequest.builder()
                    .instanceProfileName(resource.getName())
                    .roleName(desiredRole)
                    .build());
            log.debug("Added role {} to instance profile {}", desiredRole, resource.getName());
        }

        // Update tags
        if (resource.getTags() != null) {
            // Untag all existing tags
            if (current.getTags() != null && !current.getTags().isEmpty()) {
                iamClient.untagInstanceProfile(UntagInstanceProfileRequest.builder()
                        .instanceProfileName(resource.getName())
                        .tagKeys(current.getTags().keySet().stream().toList())
                        .build());
            }
            // Apply new tags
            if (!resource.getTags().isEmpty()) {
                iamClient.tagInstanceProfile(TagInstanceProfileRequest.builder()
                        .instanceProfileName(resource.getName())
                        .tags(toIamTags(resource.getTags()))
                        .build());
            }
        }

        return read(resource);
    }

    @Override
    public boolean delete(IamInstanceProfileResource resource) {
        if (resource.getName() == null) {
            log.warn("Cannot delete IAM Instance Profile without name");
            return false;
        }

        log.info("Deleting IAM Instance Profile: {}", resource.getName());

        try {
            // Get current profile to find attached roles
            var current = read(resource);
            if (current == null) {
                return false;
            }

            // Remove role from instance profile if present
            if (current.getRole() != null) {
                iamClient.removeRoleFromInstanceProfile(RemoveRoleFromInstanceProfileRequest.builder()
                        .instanceProfileName(resource.getName())
                        .roleName(current.getRole())
                        .build());
                log.debug("Removed role {} from instance profile {}", current.getRole(), resource.getName());
            }

            // Delete the instance profile
            iamClient.deleteInstanceProfile(DeleteInstanceProfileRequest.builder()
                    .instanceProfileName(resource.getName())
                    .build());

            log.info("Deleted IAM Instance Profile: {}", resource.getName());
            return true;

        } catch (NoSuchEntityException e) {
            return false;
        }
    }

    @Override
    public List<Diagnostic> validate(IamInstanceProfileResource resource) {
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

    private IamInstanceProfileResource mapToResource(InstanceProfile profile) {
        var resource = new IamInstanceProfileResource();

        // Input properties
        resource.setName(profile.instanceProfileName());
        resource.setPath(profile.path());

        // Get attached role (instance profile can have at most one role)
        if (profile.roles() != null && !profile.roles().isEmpty()) {
            resource.setRole(profile.roles().get(0).roleName());
        }

        // Tags
        if (profile.tags() != null && !profile.tags().isEmpty()) {
            resource.setTags(profile.tags().stream()
                    .collect(Collectors.toMap(Tag::key, Tag::value)));
        }

        // Cloud-managed properties
        resource.setInstanceProfileId(profile.instanceProfileId());
        resource.setArn(profile.arn());
        resource.setCreateDate(profile.createDate() != null ? profile.createDate().toString() : null);

        return resource;
    }
}
