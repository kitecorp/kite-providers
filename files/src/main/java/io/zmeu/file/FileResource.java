package io.zmeu.file;

import com.github.zafarkhaja.semver.Version;
import io.zmeu.api.Attribute;
import io.zmeu.api.Metadata;
import io.zmeu.api.ResourceDeclaration;
import io.zmeu.api.Schema;

import java.util.List;

public class FileResource extends ResourceDeclaration {
    public FileResource() {
        metadata = new Metadata("File");
        schema = new Schema();
        schema.setVersion(Version.forIntegers(0, 0, 1));
        schema.setDescription("Used to create local files");
        schema.setAttributes(List.of(
                Attribute.builder()
                        .name("name")
                        .type("String")
                        .required(true)
                        .build()
        ));
    }
}
