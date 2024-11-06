package io.zmeu.file;

import io.zmeu.api.Property;
import io.zmeu.api.Resource;
import io.zmeu.api.SchemaDefinition;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.javers.core.metamodel.annotation.TypeName;

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
@TypeName("File")
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
        if (path == null) {
            return Path.of(name);
        } else if (name == null) {
            return Path.of(path);
        } else {
            return Path.of(path + name);
        }
    }

}
