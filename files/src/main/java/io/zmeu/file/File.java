package io.zmeu.file;

import io.zmeu.api.schema.Property;
import io.zmeu.api.resource.Resource;
import io.zmeu.api.schema.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import org.javers.core.metamodel.annotation.TypeName;

import java.nio.file.Path;

import static io.zmeu.api.schema.Property.Type;


/*
 * Class name will be the resource type name as well. If class name is File, to create a resource of this type
 * it will be like this: resource File resourceName { ... }
 * */
@Data
@Schema(description = "Used to create local files", typeName = "File")
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@TypeName("File")
public class File extends Resource {
    @Property(type = Property.Type.String, name = "name", optional = false, recreateOnChange = true)
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
