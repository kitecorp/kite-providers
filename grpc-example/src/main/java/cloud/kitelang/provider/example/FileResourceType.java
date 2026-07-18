package cloud.kitelang.provider.example;

import cloud.kitelang.provider.Diagnostic;
import cloud.kitelang.provider.ResourceContext;
import cloud.kitelang.provider.ResourceTypeHandler;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * ResourceTypeHandler implementation for files.
 * Demonstrates how to implement CRUD operations for a resource.
 */
@Slf4j
public class FileResourceType extends ResourceTypeHandler<FileResource> {


    @Override
    public FileResource create(FileResource resource) {
        log.info("Creating file: {}", resource.getPath());

        try {
            var path = Path.of(resource.getPath());

            // Create parent directories if needed
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            // Write content
            String content = resource.getContent() != null ? resource.getContent() : "";
            Files.writeString(path, content, StandardCharsets.UTF_8);

            // Set permissions if specified and on POSIX system
            if (resource.getPermissions() != null) {
                setPermissions(path, resource.getPermissions());
            }

            // Compute cloud-managed properties
            resource.setChecksum(computeChecksum(content));
            resource.setLastModified(Instant.now().toString());

            return resource;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create file: " + e.getMessage(), e);
        }
    }

    @Override
    public FileResource read(FileResource resource) {
        log.info("Reading file: {}", resource.getPath());

        try {
            var path = Path.of(resource.getPath());

            if (!Files.exists(path)) {
                return null;
            }

            // Read content
            String content = Files.readString(path, StandardCharsets.UTF_8);
            resource.setContent(content);

            // Read last modified
            FileTime lastModified = Files.getLastModifiedTime(path);
            resource.setLastModified(lastModified.toInstant().toString());

            // Compute checksum
            resource.setChecksum(computeChecksum(content));

            return resource;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + e.getMessage(), e);
        }
    }

    @Override
    public FileResource update(FileResource resource) {
        log.info("Updating file: {}", resource.getPath());

        try {
            var path = Path.of(resource.getPath());

            // Write new content
            String content = resource.getContent() != null ? resource.getContent() : "";
            Files.writeString(path, content, StandardCharsets.UTF_8);

            // Update permissions if specified
            if (resource.getPermissions() != null) {
                setPermissions(path, resource.getPermissions());
            }

            // Update cloud-managed properties
            resource.setChecksum(computeChecksum(content));
            resource.setLastModified(Instant.now().toString());

            return resource;
        } catch (IOException e) {
            throw new RuntimeException("Failed to update file: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean delete(FileResource resource) {
        log.info("Deleting file: {}", resource.getPath());

        try {
            var path = Path.of(resource.getPath());
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file: " + e.getMessage(), e);
        }
    }

    /**
     * Creates the file and hands the applied checksum back as provider-private
     * bytes ({@code sha256:<hex>}). Demonstrates the {@link ResourceContext}
     * round-trip: the engine persists the bytes and supplies them on later
     * operations, so a fresh provider process can prove state survived.
     */
    @Override
    public FileResource create(FileResource resource, ResourceContext<FileResource> context) {
        var created = create(resource);
        context.returnPrivateData(privateDataFor(created));
        return created;
    }

    /**
     * Updates the file and refreshes the private bytes to the new checksum.
     */
    @Override
    public FileResource update(FileResource resource, ResourceContext<FileResource> context) {
        var updated = update(resource);
        context.returnPrivateData(privateDataFor(updated));
        return updated;
    }

    /**
     * Private bytes for a resource: the applied content checksum, tagged so
     * tests can assert the exact round-tripped value.
     */
    private byte[] privateDataFor(FileResource resource) {
        return ("sha256:" + resource.getChecksum()).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public List<Diagnostic> validate(FileResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getPath() == null || resource.getPath().isBlank()) {
            diagnostics.add(Diagnostic.error("Path is required")
                    .withProperty("path"));
        }

        return diagnostics;
    }

    /**
     * Compute SHA256 checksum of content.
     */
    private String computeChecksum(String content) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Set POSIX file permissions from octal string.
     */
    private void setPermissions(Path path, String permissions) {
        try {
            // Convert octal string to POSIX permissions
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString(
                    octalToPermissionString(permissions));
            Files.setPosixFilePermissions(path, perms);
        } catch (Exception e) {
            log.warn("Failed to set permissions: {}", e.getMessage());
        }
    }

    /**
     * Convert octal permission (e.g., "644") to rwx string (e.g., "rw-r--r--").
     */
    private String octalToPermissionString(String octal) {
        if (octal.startsWith("0")) {
            octal = octal.substring(1);
        }
        if (octal.length() != 3) {
            return "rw-r--r--"; // default
        }

        StringBuilder sb = new StringBuilder();
        for (char c : octal.toCharArray()) {
            int digit = Character.getNumericValue(c);
            sb.append((digit & 4) != 0 ? 'r' : '-');
            sb.append((digit & 2) != 0 ? 'w' : '-');
            sb.append((digit & 1) != 0 ? 'x' : '-');
        }
        return sb.toString();
    }
}
