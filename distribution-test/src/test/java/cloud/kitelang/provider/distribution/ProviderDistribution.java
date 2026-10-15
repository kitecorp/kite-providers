package cloud.kitelang.provider.distribution;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * An installed provider distribution: an {@code installDist} output or an unzipped
 * release zip, described by the {@code provider.json} at its root.
 *
 * @param name       the provider name from {@code provider.json}
 * @param version    the provider version from {@code provider.json}
 * @param directory  the distribution root
 * @param executable the launcher {@code provider.json} names, the one the engine starts
 */
record ProviderDistribution(String name, String version, Path directory, Path executable) {

    private static final ObjectMapper JSON = new ObjectMapper();

    static ProviderDistribution read(Path directory) {
        var manifest = directory.resolve("provider.json");
        if (!Files.isRegularFile(manifest)) {
            throw new IllegalArgumentException(
                    "Not a provider distribution, it has no provider.json: " + directory.toAbsolutePath());
        }
        try {
            var json = JSON.readTree(manifest.toFile());
            return new ProviderDistribution(
                    json.path("name").asText(),
                    json.path("version").asText(),
                    directory,
                    directory.resolve(json.path("executable").asText()));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + manifest, e);
        }
    }

    /** Names the parameterized test case. */
    @Override
    public String toString() {
        return name + " " + version;
    }
}
