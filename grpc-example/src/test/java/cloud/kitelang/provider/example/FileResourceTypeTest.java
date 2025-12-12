package cloud.kitelang.provider.example;

import cloud.kitelang.provider.Diagnostic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FileResourceType CRUD operations.
 */
class FileResourceTypeTest {

    private FileResourceType resourceType;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        resourceType = new FileResourceType();
    }

    @Test
    void createFile() {
        var resource = new FileResource();
        resource.setPath(tempDir.resolve("test.txt").toString());
        resource.setContent("Hello, World!");

        var created = resourceType.create(resource);

        assertNotNull(created);
        assertEquals("Hello, World!", created.getContent());
        assertNotNull(created.getChecksum());
        assertNotNull(created.getLastModified());
        assertTrue(Files.exists(Path.of(created.getPath())));
    }

    @Test
    void createFileWithEmptyContent() {
        var resource = new FileResource();
        resource.setPath(tempDir.resolve("empty.txt").toString());

        var created = resourceType.create(resource);

        assertNotNull(created);
        assertTrue(Files.exists(Path.of(created.getPath())));
    }

    @Test
    void readExistingFile() throws IOException {
        var filePath = tempDir.resolve("existing.txt");
        Files.writeString(filePath, "Existing content");

        var resource = new FileResource();
        resource.setPath(filePath.toString());

        var read = resourceType.read(resource);

        assertNotNull(read);
        assertEquals("Existing content", read.getContent());
        assertNotNull(read.getChecksum());
        assertNotNull(read.getLastModified());
    }

    @Test
    void readNonExistentFile() {
        var resource = new FileResource();
        resource.setPath(tempDir.resolve("nonexistent.txt").toString());

        var read = resourceType.read(resource);

        assertNull(read);
    }

    @Test
    void updateFile() throws IOException {
        var filePath = tempDir.resolve("update.txt");
        Files.writeString(filePath, "Original content");

        var resource = new FileResource();
        resource.setPath(filePath.toString());
        resource.setContent("Updated content");

        var updated = resourceType.update(resource);

        assertNotNull(updated);
        assertEquals("Updated content", updated.getContent());
        assertEquals("Updated content", Files.readString(filePath));
    }

    @Test
    void deleteFile() throws IOException {
        var filePath = tempDir.resolve("delete.txt");
        Files.writeString(filePath, "To be deleted");

        var resource = new FileResource();
        resource.setPath(filePath.toString());

        boolean deleted = resourceType.delete(resource);

        assertTrue(deleted);
        assertFalse(Files.exists(filePath));
    }

    @Test
    void deleteNonExistentFile() {
        var resource = new FileResource();
        resource.setPath(tempDir.resolve("nonexistent.txt").toString());

        boolean deleted = resourceType.delete(resource);

        assertFalse(deleted);
    }

    @Test
    void validateMissingPath() {
        var resource = new FileResource();

        var diagnostics = resourceType.validate(resource);

        assertEquals(1, diagnostics.size());
        assertEquals(Diagnostic.Severity.ERROR, diagnostics.get(0).getSeverity());
        assertTrue(diagnostics.get(0).getSummary().contains("Path"));
    }

    @Test
    void validateBlankPath() {
        var resource = new FileResource();
        resource.setPath("   ");

        var diagnostics = resourceType.validate(resource);

        assertEquals(1, diagnostics.size());
    }

    @Test
    void validateValidResource() {
        var resource = new FileResource();
        resource.setPath("/some/path.txt");

        var diagnostics = resourceType.validate(resource);

        assertTrue(diagnostics.isEmpty());
    }

    @Test
    void checksumConsistency() {
        var resource1 = new FileResource();
        resource1.setPath(tempDir.resolve("checksum1.txt").toString());
        resource1.setContent("Same content");

        var resource2 = new FileResource();
        resource2.setPath(tempDir.resolve("checksum2.txt").toString());
        resource2.setContent("Same content");

        var created1 = resourceType.create(resource1);
        var created2 = resourceType.create(resource2);

        assertEquals(created1.getChecksum(), created2.getChecksum());
    }
}
