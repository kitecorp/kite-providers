package cloud.kitelang.providers.files;

import cloud.kitelang.api.annotations.Cloud;
import cloud.kitelang.api.annotations.Property;
import cloud.kitelang.api.annotations.TypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Example file resource for the gRPC provider.
 * Demonstrates how to define resources with properties and cloud-managed fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("File")
public class FileResource {

    /**
     * Path to the file.
     */
    @Property
    private String path;

    /**
     * File content.
     */
    @Property
    private String content;

    /**
     * File permissions (e.g., "0644").
     */
    @Property
    private String permissions;

    /**
     * SHA256 checksum of the content (computed by cloud).
     */
    @Cloud
    @Property
    private String checksum;

    /**
     * Last modified timestamp (computed by cloud).
     */
    @Cloud
    @Property
    private String lastModified;
}
