package io.zmeu.file;

import io.zmeu.api.Provider;
import io.zmeu.api.Resources;
import org.pf4j.Extension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    public FileResource read(FileResource resource) {
        if (resource.getPath() == null && resource.getName() == null) {
            throw new IllegalArgumentException("Path and name can't be null at the same time");
        }
        if (resource.getPath() == null) {
            var content = readContent(resource.getName());
            resource.setContent(content);
            return resource;
        } else if (resource.getName() == null) {
            var content = readContent(resource.getPath());
            resource.setContent(content);
            return resource;
        }

        throw new IllegalArgumentException("Error for "+ resource);
    }

    private static String readContent(String filename) {
        try {
            return Files.readString(Path.of(filename));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}