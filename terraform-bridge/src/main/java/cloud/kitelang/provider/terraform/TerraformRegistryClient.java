package cloud.kitelang.provider.terraform;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipInputStream;

/**
 * Client for the public Terraform Registry (registry.terraform.io).
 *
 * <p>Downloads Terraform provider binaries and caches them locally under a structured
 * directory layout: {@code {cacheDir}/{namespace}/{type}/{version}/terraform-provider-{type}}.
 *
 * <p>Supports Terraform-style version constraints:
 * <ul>
 *   <li>{@code ~> 5.0} — pessimistic: {@code >= 5.0.0, < 6.0.0}</li>
 *   <li>{@code ~> 5.82.0} — pessimistic: {@code >= 5.82.0, < 5.83.0}</li>
 *   <li>{@code >= 5.0} — minimum version</li>
 *   <li>{@code = 5.82.0} or {@code 5.82.0} — exact version</li>
 *   <li>{@code >= 5.0, < 6.0} — compound (comma-separated)</li>
 *   <li>{@code null} or empty — latest available</li>
 * </ul>
 *
 * @see <a href="https://developer.hashicorp.com/terraform/registry/api-docs">Terraform Registry API</a>
 */
@Slf4j
public class TerraformRegistryClient {

    private static final String REGISTRY_BASE = "https://registry.terraform.io/v1/providers";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path cacheDir;
    private final HttpClient httpClient;

    /**
     * Creates a new registry client with the given local cache directory.
     *
     * @param cacheDir root directory for cached provider binaries
     *                 (e.g. {@code ~/.kite/providers/tf/})
     */
    public TerraformRegistryClient(Path cacheDir) {
        this.cacheDir = cacheDir;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(HTTP_TIMEOUT)
                .build();
    }

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    /**
     * Ensures a provider binary is available locally, downloading it from the
     * Terraform Registry if not already cached.
     *
     * @param providerAddress address in the form {@code "aws"} (defaults to hashicorp namespace)
     *                        or {@code "hashicorp/aws"}
     * @param versionConstraint Terraform-style version constraint, or null/empty for latest
     * @return path to the executable provider binary
     * @throws IOException if download or filesystem operations fail
     */
    public Path ensureProvider(String providerAddress, String versionConstraint) throws IOException {
        var address = parseProviderAddress(providerAddress);
        var namespace = address.namespace();
        var type = address.type();

        log.info("Ensuring provider {}/{} with constraint '{}'", namespace, type, versionConstraint);

        // Check if a specific version is already cached (exact match only)
        if (isExactVersion(versionConstraint) && isCached(namespace, type, versionConstraint.trim())) {
            var cachedPath = getCachedPath(namespace, type, versionConstraint.trim());
            log.info("Provider {}/{} v{} already cached at {}", namespace, type, versionConstraint.trim(), cachedPath);
            return cachedPath;
        }

        // Fetch available versions from registry
        var availableVersions = listVersions(namespace, type);
        var resolvedVersion = resolveVersion(availableVersions, versionConstraint);
        log.info("Resolved version constraint '{}' to {}", versionConstraint, resolvedVersion);

        // Check cache with resolved version
        if (isCached(namespace, type, resolvedVersion)) {
            var cachedPath = getCachedPath(namespace, type, resolvedVersion);
            log.info("Provider {}/{} v{} already cached at {}", namespace, type, resolvedVersion, cachedPath);
            return cachedPath;
        }

        // Download the provider binary
        return downloadProvider(namespace, type, resolvedVersion);
    }

    /**
     * Lists available versions for a provider from the Terraform Registry.
     *
     * @param namespace provider namespace (e.g. {@code "hashicorp"})
     * @param type provider type (e.g. {@code "aws"})
     * @return list of version strings
     * @throws IOException if the HTTP call fails
     */
    public List<String> listVersions(String namespace, String type) throws IOException {
        var url = "%s/%s/%s/versions".formatted(REGISTRY_BASE, namespace, type);
        log.debug("Fetching provider versions from {}", url);

        var responseBody = httpGet(url);
        var versionsResponse = JSON.readValue(responseBody, VersionsResponse.class);

        return versionsResponse.versions().stream()
                .map(VersionEntry::version)
                .toList();
    }

    /**
     * Resolves a Terraform-style version constraint against a list of available versions.
     *
     * <p>Returns the highest version that satisfies all constraints. Supports:
     * exact, pessimistic ({@code ~>}), comparison ({@code >=}, {@code <=}, {@code >}, {@code <}),
     * compound (comma-separated), and null/empty (latest).
     *
     * @param availableVersions list of available version strings
     * @param constraint the version constraint string, or null/empty for latest
     * @return the resolved version string
     * @throws IllegalArgumentException if no version matches the constraint
     */
    public String resolveVersion(List<String> availableVersions, String constraint) {
        if (availableVersions == null || availableVersions.isEmpty()) {
            throw new IllegalArgumentException("No versions available to resolve against");
        }

        // Sort descending so we pick the highest match first
        var sorted = availableVersions.stream()
                .sorted((a, b) -> compareVersions(b, a))
                .toList();

        // Null, empty, or blank → latest
        if (constraint == null || constraint.isBlank()) {
            return sorted.getFirst();
        }

        var trimmed = constraint.trim();

        // Compound constraints: split on comma and check all
        var constraints = Arrays.stream(trimmed.split(","))
                .map(String::trim)
                .toList();

        return sorted.stream()
                .filter(version -> constraints.stream().allMatch(c -> satisfiesConstraint(version, c)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No version from %s satisfies constraint '%s'".formatted(availableVersions, constraint)));
    }

    /**
     * Checks whether a provider binary is already cached and executable.
     *
     * @param namespace provider namespace
     * @param type provider type
     * @param version provider version
     * @return true if the binary exists and is executable
     */
    public boolean isCached(String namespace, String type, String version) {
        var path = getCachedPath(namespace, type, version);
        return Files.exists(path) && path.toFile().canExecute();
    }

    /**
     * Returns the filesystem path where a provider binary would be cached.
     *
     * @param namespace provider namespace (e.g. {@code "hashicorp"})
     * @param type provider type (e.g. {@code "aws"})
     * @param version provider version (e.g. {@code "5.82.0"})
     * @return path to the binary: {@code {cacheDir}/{namespace}/{type}/{version}/terraform-provider-{type}}
     */
    public Path getCachedPath(String namespace, String type, String version) {
        return cacheDir
                .resolve(namespace)
                .resolve(type)
                .resolve(version)
                .resolve("terraform-provider-" + type);
    }

    // ---------------------------------------------------------------
    // Provider address parsing
    // ---------------------------------------------------------------

    /**
     * Parsed provider address containing namespace and type.
     *
     * @param namespace the provider namespace (e.g. {@code "hashicorp"})
     * @param type the provider type (e.g. {@code "aws"})
     */
    public record ProviderAddress(String namespace, String type) {}

    /**
     * Parses a provider address string into namespace and type components.
     *
     * <p>If only a type is given (e.g. {@code "aws"}), the namespace defaults
     * to {@code "hashicorp"}.
     *
     * @param address provider address like {@code "aws"} or {@code "hashicorp/aws"}
     * @return parsed address with namespace and type
     * @throws IllegalArgumentException if the address is null or empty
     */
    public static ProviderAddress parseProviderAddress(String address) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("Provider address must not be null or empty");
        }

        var trimmed = address.trim();
        var slashIndex = trimmed.indexOf('/');

        if (slashIndex < 0) {
            return new ProviderAddress("hashicorp", trimmed);
        }

        var namespace = trimmed.substring(0, slashIndex);
        var type = trimmed.substring(slashIndex + 1);
        return new ProviderAddress(namespace, type);
    }

    // ---------------------------------------------------------------
    // Platform detection
    // ---------------------------------------------------------------

    /**
     * Maps a Java {@code os.name} value to the Terraform platform OS string.
     *
     * @param osName the value of {@code System.getProperty("os.name")}
     * @return terraform-style OS name ({@code "darwin"}, {@code "linux"}, or {@code "windows"})
     */
    static String mapOsName(String osName) {
        if (osName == null) {
            throw new IllegalArgumentException("OS name must not be null");
        }
        var lower = osName.toLowerCase();
        if (lower.contains("mac") || lower.contains("darwin")) return "darwin";
        if (lower.contains("linux")) return "linux";
        if (lower.contains("windows")) return "windows";
        throw new IllegalArgumentException("Unsupported OS: " + osName);
    }

    /**
     * Maps a Java {@code os.arch} value to the Terraform platform architecture string.
     *
     * @param archName the value of {@code System.getProperty("os.arch")}
     * @return terraform-style architecture ({@code "amd64"} or {@code "arm64"})
     */
    static String mapArchName(String archName) {
        if (archName == null) {
            throw new IllegalArgumentException("Architecture name must not be null");
        }
        return switch (archName) {
            case "aarch64" -> "arm64";
            case "x86_64", "amd64" -> "amd64";
            default -> archName;
        };
    }

    /**
     * Detects the current platform's OS string for the Terraform Registry.
     */
    private static String detectOs() {
        return mapOsName(System.getProperty("os.name"));
    }

    /**
     * Detects the current platform's architecture string for the Terraform Registry.
     */
    private static String detectArch() {
        return mapArchName(System.getProperty("os.arch"));
    }

    // ---------------------------------------------------------------
    // Version comparison (semantic versioning)
    // ---------------------------------------------------------------

    /**
     * Compares two semantic version strings numerically, segment by segment.
     *
     * <p>Shorter versions are zero-padded: {@code "5.0"} equals {@code "5.0.0"}.
     *
     * @param a first version string
     * @param b second version string
     * @return negative if a &lt; b, zero if equal, positive if a &gt; b
     */
    static int compareVersions(String a, String b) {
        var partsA = a.split("\\.");
        var partsB = b.split("\\.");
        var maxLen = Math.max(partsA.length, partsB.length);

        for (var i = 0; i < maxLen; i++) {
            var segA = i < partsA.length ? Integer.parseInt(partsA[i]) : 0;
            var segB = i < partsB.length ? Integer.parseInt(partsB[i]) : 0;
            if (segA != segB) {
                return Integer.compare(segA, segB);
            }
        }
        return 0;
    }

    // ---------------------------------------------------------------
    // Version constraint evaluation
    // ---------------------------------------------------------------

    /**
     * Tests whether a single version satisfies a single constraint clause.
     *
     * @param version the version to test
     * @param constraint a single constraint (e.g. {@code ">= 5.0"}, {@code "~> 5.82.0"})
     * @return true if the version satisfies the constraint
     */
    private static boolean satisfiesConstraint(String version, String constraint) {
        var trimmed = constraint.trim();

        // Pessimistic operator: ~>
        if (trimmed.startsWith("~>")) {
            return satisfiesPessimistic(version, trimmed.substring(2).trim());
        }

        // Comparison operators (order matters: >= before >, <= before <)
        if (trimmed.startsWith(">=")) {
            return compareVersions(version, normalizeVersion(trimmed.substring(2).trim())) >= 0;
        }
        if (trimmed.startsWith("<=")) {
            return compareVersions(version, normalizeVersion(trimmed.substring(2).trim())) <= 0;
        }
        if (trimmed.startsWith(">")) {
            return compareVersions(version, normalizeVersion(trimmed.substring(1).trim())) > 0;
        }
        if (trimmed.startsWith("<")) {
            return compareVersions(version, normalizeVersion(trimmed.substring(1).trim())) < 0;
        }
        if (trimmed.startsWith("=")) {
            return compareVersions(version, normalizeVersion(trimmed.substring(1).trim())) == 0;
        }

        // Bare version string → exact match
        return compareVersions(version, normalizeVersion(trimmed)) == 0;
    }

    /**
     * Evaluates the pessimistic constraint operator ({@code ~>}).
     *
     * <p>The rightmost segment is allowed to increment freely; the segment before it
     * defines the upper boundary:
     * <ul>
     *   <li>{@code ~> 5.0} (2 segments) → {@code >= 5.0.0, < 6.0.0}</li>
     *   <li>{@code ~> 5.82} (2 segments) → {@code >= 5.82.0, < 6.0.0}</li>
     *   <li>{@code ~> 5.82.0} (3 segments) → {@code >= 5.82.0, < 5.83.0}</li>
     * </ul>
     */
    private static boolean satisfiesPessimistic(String version, String constraintVersion) {
        var parts = constraintVersion.split("\\.");
        var lowerBound = normalizeVersion(constraintVersion);

        // Build the upper bound by incrementing the second-to-last segment
        // and dropping everything after it.
        // For 2 segments (X.Y): increment X → upper is (X+1).0
        // For 3 segments (X.Y.Z): increment Y → upper is X.(Y+1).0
        var upperParts = new int[parts.length];
        for (var i = 0; i < parts.length; i++) {
            upperParts[i] = Integer.parseInt(parts[i]);
        }

        var incrementIndex = parts.length - 2;
        if (incrementIndex < 0) {
            // Single segment (unusual, treat like exact)
            incrementIndex = 0;
        }
        upperParts[incrementIndex]++;

        // Zero out all segments after the incremented one
        for (var i = incrementIndex + 1; i < upperParts.length; i++) {
            upperParts[i] = 0;
        }

        var upperBound = buildVersionString(upperParts);

        return compareVersions(version, lowerBound) >= 0
                && compareVersions(version, upperBound) < 0;
    }

    /**
     * Normalizes a version string to at least 3 segments (e.g. {@code "5.0"} → {@code "5.0.0"}).
     */
    private static String normalizeVersion(String version) {
        var parts = version.split("\\.");
        if (parts.length >= 3) return version;
        var sb = new StringBuilder(version);
        for (var i = parts.length; i < 3; i++) {
            sb.append(".0");
        }
        return sb.toString();
    }

    /**
     * Builds a dot-separated version string from integer segments.
     */
    private static String buildVersionString(int[] segments) {
        var sb = new StringBuilder();
        for (var i = 0; i < segments.length; i++) {
            if (i > 0) sb.append('.');
            sb.append(segments[i]);
        }
        return sb.toString();
    }

    /**
     * Checks whether a constraint string represents an exact version (no operators).
     */
    private static boolean isExactVersion(String constraint) {
        if (constraint == null || constraint.isBlank()) return false;
        var trimmed = constraint.trim();
        return !trimmed.startsWith("~>") && !trimmed.startsWith(">=") && !trimmed.startsWith("<=")
                && !trimmed.startsWith(">") && !trimmed.startsWith("<") && !trimmed.startsWith("=")
                && !trimmed.contains(",");
    }

    // ---------------------------------------------------------------
    // Download flow
    // ---------------------------------------------------------------

    /**
     * Downloads a provider binary from the Terraform Registry, verifies its checksum,
     * extracts it from the zip archive, and caches it locally.
     */
    private Path downloadProvider(String namespace, String type, String version) throws IOException {
        var os = detectOs();
        var arch = detectArch();
        log.info("Downloading provider {}/{} v{} for {}/{}", namespace, type, version, os, arch);

        // 1. Get download metadata
        var downloadUrl = "%s/%s/%s/%s/download/%s/%s".formatted(
                REGISTRY_BASE, namespace, type, version, os, arch);
        var metadataBody = httpGet(downloadUrl);
        var metadata = JSON.readValue(metadataBody, DownloadMetadata.class);

        // 2. Download the zip to a temp file
        var tempZip = Files.createTempFile("terraform-provider-", ".zip");
        try {
            downloadFile(metadata.downloadUrl(), tempZip);

            // 3. Verify SHA256 checksum
            if (metadata.shasum() != null && !metadata.shasum().isBlank()) {
                verifySha256(tempZip, metadata.shasum());
            }

            // 4. Extract the binary from the zip
            var binaryPath = getCachedPath(namespace, type, version);
            Files.createDirectories(binaryPath.getParent());
            extractBinaryFromZip(tempZip, binaryPath, type);

            // 5. Set executable permission
            if (!binaryPath.toFile().setExecutable(true)) {
                log.warn("Failed to set executable permission on {}", binaryPath);
            }

            log.info("Provider {}/{} v{} cached at {}", namespace, type, version, binaryPath);
            return binaryPath;
        } finally {
            Files.deleteIfExists(tempZip);
        }
    }

    /**
     * Sends an HTTP GET request and returns the response body as a string.
     *
     * @param url the URL to fetch
     * @return the response body
     * @throws IOException if the request fails or returns a non-2xx status
     */
    private String httpGet(String url) throws IOException {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();

        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("HTTP %d from %s: %s".formatted(
                        response.statusCode(), url, response.body()));
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted: " + url, e);
        }
    }

    /**
     * Downloads a file from the given URL to the target path.
     */
    private void downloadFile(String url, Path target) throws IOException {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();

        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("HTTP %d downloading %s".formatted(response.statusCode(), url));
            }
            try (InputStream body = response.body()) {
                Files.copy(body, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted: " + url, e);
        }
    }

    /**
     * Verifies the SHA256 checksum of a file.
     *
     * @param file the file to verify
     * @param expectedSha256 the expected hex-encoded SHA256 hash
     * @throws IOException if the checksum does not match or the file cannot be read
     */
    private static void verifySha256(Path file, String expectedSha256) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var is = Files.newInputStream(file)) {
                var buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            var actualHash = HexFormat.of().formatHex(digest.digest());

            if (!actualHash.equalsIgnoreCase(expectedSha256)) {
                throw new IOException(
                        "SHA256 mismatch: expected %s, got %s".formatted(expectedSha256, actualHash));
            }
            log.debug("SHA256 checksum verified: {}", actualHash);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Extracts the provider binary from a zip archive.
     *
     * <p>Looks for a file named {@code terraform-provider-{type}} (with or without {@code .exe})
     * inside the zip and copies it to the target path.
     *
     * @param zipFile path to the zip archive
     * @param targetPath path where the binary should be written
     * @param type the provider type name (to locate the correct entry)
     * @throws IOException if the zip does not contain the expected binary
     */
    private static void extractBinaryFromZip(Path zipFile, Path targetPath, String type)
            throws IOException {
        var binaryPrefix = "terraform-provider-" + type;

        try (var zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            var entry = zis.getNextEntry();
            while (entry != null) {
                var entryName = entry.getName();
                // Match the binary by prefix (handles .exe suffix on Windows)
                if (!entry.isDirectory() && entryName.contains(binaryPrefix)) {
                    Files.copy(zis, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    log.debug("Extracted {} from zip", entryName);
                    return;
                }
                entry = zis.getNextEntry();
            }
        }

        throw new IOException(
                "Provider binary '%s' not found in zip archive %s".formatted(binaryPrefix, zipFile));
    }

    // ---------------------------------------------------------------
    // JSON response DTOs (records)
    // ---------------------------------------------------------------

    /** Response from the versions API endpoint. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record VersionsResponse(List<VersionEntry> versions) {}

    /** A single version entry from the versions API. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record VersionEntry(String version, List<String> protocols, List<PlatformEntry> platforms) {}

    /** A platform entry (os/arch) from the versions API. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PlatformEntry(String os, String arch) {}

    /** Response from the download URL API endpoint. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record DownloadMetadata(
            String os,
            String arch,
            String filename,
            @com.fasterxml.jackson.annotation.JsonProperty("download_url") String downloadUrl,
            String shasum,
            List<String> protocols
    ) {}
}
