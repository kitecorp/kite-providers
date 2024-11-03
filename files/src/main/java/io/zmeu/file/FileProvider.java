package io.zmeu.file;

import io.zmeu.api.Provider;
import io.zmeu.api.Resources;
import org.pf4j.Extension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Extension
public class FileProvider implements Provider<File> {

    @Override
    public Resources<File> resources() {
        var resource = new File();

        return new Resources<>(List.of(resource));
    }

    @Override
    public File read(File resource) {
        if (resource.getPath() == null && resource.getName() == null) {
            throw new IllegalArgumentException("Path and name can't be null at the same time");
        }
        try {
            if (resource.getPath() == null) {
                var content = readContent(resource.getName());
                resource.setContent(content);
                return resource;
            } else if (resource.getResourceName() == null) {
                var content = readContent(resource.getPath());
                resource.setContent(content);
                return resource;
            } else {
                var content = readContent(resource.getPath() + resource.getName());
                resource.setContent(content);
                return resource;
            }
        } catch (RuntimeException exception) {
            return null;
        }
    }

    @Override
    public String namespace() {
        return "File";
    }

    @Override
    public String resourceType() {
        return File.class.getName();
    }

    private static String readContent(String filename) {
        try {
            return Files.readString(Path.of(filename));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}