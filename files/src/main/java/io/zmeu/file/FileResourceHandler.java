package io.zmeu.file;

import io.zmeu.api.ResourceHandler;
import org.pf4j.Extension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

@Extension
public class FileResourceHandler extends ResourceHandler<File> {

    @Override
    public File read(File resource) {
        requirePathOrName(resource);
        try {
            if (resource.getPath() == null) {
                var content = readContent(resource.getName());
                return File.builder()
                        .content(content)
                        .name(resource.getName())
                        .path(Paths.get(resource.getName()).toString())
                        .build();
            } else if (resource.getName() == null) {
                var content = readContent(resource.getPath());
                return File.builder()
                        .content(content)
                        .path(resource.getPath())
                        .build();
            } else {
                var content = readContent(resource.path().toString());
                return File.builder()
                        .content(content)
                        .name(resource.getName())
                        .path(resource.getPath())
                        .build();
            }
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static void requirePathOrName(File resource) {
        if (resource.getPath() == null && resource.getName() == null) {
            throw new IllegalArgumentException("Path and name can't be null at the same time: " + resource);
        }
    }

    @Override
    public File create(File resource) {
        requirePathOrName(resource);
        return writeFile(resource, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    @Override
    public File update(File resource) {
        requirePathOrName(resource);
        File oldResource = read(resource);
        requirePathOrName(oldResource);

        try {
            if (resource.getPath() == null) {
                resource.setPath(oldResource.getPath());
            } else if (!oldResource.path().equals(resource.path())) {
                Files.move(Paths.get(oldResource.getPath()), Paths.get(resource.getPath()), StandardCopyOption.REPLACE_EXISTING);
            } else if (oldResource.getContent().length() != resource.getContent().length()) {
                writeFile(resource, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            }
        } catch (RuntimeException | IOException e) {
            return resource;
        }
        return resource;
    }

    private static File writeFile(File resource, StandardOpenOption... options) {
        try (var writer = Files.newBufferedWriter(resource.path(), StandardCharsets.UTF_8, options)) {
            writer.write(resource.getContent());
            return resource;
        } catch (RuntimeException | IOException e) {
            return resource;
        }
    }

    @Override
    public boolean delete(File resource) {
        requirePathOrName(resource);
        if (resource.getPath() == null) {
            return Paths.get(resource.getName()).toFile().delete();
        } else if (resource.getName() == null) {
            return Paths.get(resource.getPath()).toFile().delete();
        } else {
            return resource.path().toFile().delete();
        }
    }

    private static String readContent(String filename) {
        try {
            return Files.readString(Path.of(filename));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}