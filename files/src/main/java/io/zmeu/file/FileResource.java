package io.zmeu.file;

import io.zmeu.api.Property;
import io.zmeu.api.Resource;
import io.zmeu.api.Schema;
import io.zmeu.api.types.Types;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.nio.file.Path;
import java.util.Optional;

@Data
@Schema(description = "Used to create local files", typeName = "Std.File")
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class FileResource extends Resource {
    @Property(type = Types.String, name = "name", optional = false)
    private String name;
    @Property(type = Types.String)
    private String content;
    @Property(type = Types.String)
    private String path;


    public Path path() {
        return Path.of(Optional.ofNullable(path).orElse(name));
    }

    public void setContent(String content) {
        this.content = content;
    }
}
