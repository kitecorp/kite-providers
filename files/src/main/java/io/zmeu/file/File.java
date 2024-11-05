package io.zmeu.file;

import io.zmeu.api.Property;
import io.zmeu.api.Resource;
import io.zmeu.api.SchemaDefinition;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.nio.file.Path;
import java.util.Optional;

import static io.zmeu.api.Property.Type;


/*
 * Class name will be the resource type name as well. If class name is File, to create a resource of this type
 * it will be like this: resource File resourceName { ... }
 * */
@Data
@SchemaDefinition(description = "Used to create local files", typeName = "File")
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
public class File extends Resource {
    @Property(type = Property.Type.String, name = "name", optional = false)
    private String name;
    @Property(type = Type.String)
    private String content;
    @Property(type = Type.String)
    private String path;

    public File() {
    }

    public Path path() {
        return Path.of(Optional.ofNullable(path).orElse(name));
    }

}
