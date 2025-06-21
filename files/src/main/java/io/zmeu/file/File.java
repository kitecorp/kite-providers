package io.zmeu.file;

import io.zmeu.api.annotations.Property;
import io.zmeu.api.annotations.Schema;
import io.zmeu.api.annotations.TypeName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.nio.file.Path;


/*
 * Class name will be the resource type name as well. If class name is File, to create a resource of this type
 * it will be like this: resource File resourceName { ... }
 * */
@Data
@Schema(description = "Used to create local files")
@SuperBuilder
@EqualsAndHashCode
@AllArgsConstructor
@TypeName("File")
public class File {
    @Property(name = "name", optional = false, immutable = true)
    private String name;
    @Property
    private String content;
    @Property
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
