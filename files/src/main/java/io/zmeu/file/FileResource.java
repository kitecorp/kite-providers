package io.zmeu.file;

import io.zmeu.api.Property;
import io.zmeu.api.ResourceDeclaration;
import io.zmeu.api.Schema;
import lombok.Data;

import java.nio.file.Path;
import java.util.Optional;

@Data
@Schema(description = "Used to create local files", typeName = "File")
public class FileResource implements ResourceDeclaration {
    @Property(type = "String", name = "name", optional = false)
    private String name;
    @Property(type = "String")
    private String content;
    @Property(type = "String")
    private String path;

    public FileResource() {
    }

    public Path path() {
        return Path.of(Optional.ofNullable(path).orElse(name));
    }

}
