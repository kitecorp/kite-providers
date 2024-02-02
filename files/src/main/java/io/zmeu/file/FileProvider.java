package io.zmeu.file;

import io.zmeu.api.Provider;
import io.zmeu.api.Resources;
import org.pf4j.Extension;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

@Extension
public class FileProvider implements Provider<FileResource> {

    @Override
    public Resources getResources() {
        var resource = new FileResource();

        return Resources.builder()
                .resources(List.of(resource))
                .build();
    }

    @Override
    public FileResource read(FileResource declaration) throws FileNotFoundException {
        var path = declaration.path();

        if (Files.exists(path)) {
            try {
                var x = Files.readString(path);
                declaration.setContent(x);
                return declaration;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        throw new FileNotFoundException("File not found at: " + path);
    }

}