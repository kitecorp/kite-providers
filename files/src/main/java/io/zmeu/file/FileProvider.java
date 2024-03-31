package io.zmeu.file;

import io.zmeu.api.Provider;
import io.zmeu.api.Resources;
import org.pf4j.Extension;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

@Extension
public class FileProvider implements Provider<FileResource> {

    @Override
    public Resources resources() {
        var resource = new FileResource();

        return Resources.builder()
                .list(List.of(resource))
                .build();
    }

    @Override
    public FileResource read(FileResource declaration) {
        var path = declaration.path();
        try {
            var x = Files.readString(path.resolve(declaration.getName()));
            declaration.setContent(x);
            return declaration;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}