package io.zmeu.file;

import io.zmeu.api.Property;
import io.zmeu.api.Resource;
import io.zmeu.api.SchemaDefinition;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.nio.file.Path;
import java.util.Optional;

import static io.zmeu.api.Property.*;

@Data
@SchemaDefinition(description = "Used to create local files", typeName = "File")
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class FileResource extends Resource {
    @Property(type = Type.String, name = "name", optional = false)
    private String name;
    @Property(type = Type.String)
    private String content;
    @Property(type = Type.String)
    private String path;


    public Path path() {
        return Path.of(Optional.ofNullable(path).orElse(name));
    }

}
