package cloud.kitelang.provider.terraform;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.health.v1.HealthGrpc;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Client that manages a Terraform provider Go binary via the HashiCorp go-plugin protocol.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>Launch the provider binary as a subprocess with the magic cookie env var</li>
 *   <li>Read the handshake line from stdout: {@code CORE_PROTO|APP_PROTO|NETWORK_TYPE|NETWORK_ADDR|PROTOCOL}</li>
 *   <li>Establish a gRPC channel to the address from the handshake</li>
 *   <li>Verify health via gRPC Health Check (service {@code "plugin"})</li>
 * </ol>
 *
 * <p>On {@link #close()}: sends a {@code Stop} RPC, shuts down the gRPC channel,
 * and destroys the subprocess.</p>
 *
 * @see <a href="https://github.com/hashicorp/go-plugin/blob/main/docs/guide-plugin-write-non-go.md">go-plugin non-Go guide</a>
 */
@Slf4j
public class GoPluginClient implements AutoCloseable {

    /** Magic cookie key required by all Terraform providers. */
    static final String MAGIC_COOKIE_KEY = "TF_PLUGIN_MAGIC_COOKIE";

    /** Magic cookie value required by all Terraform providers. */
    static final String MAGIC_COOKIE_VALUE = "d602bf8f470bc67ca7faa0945738d352";

    /** Maximum time to wait for the handshake line from the provider process. */
    private static final long HANDSHAKE_TIMEOUT_SECONDS = 30;

    /** Expected core protocol version. */
    private static final int SUPPORTED_CORE_PROTOCOL = 1;

    /** Expected wire protocol. */
    private static final String SUPPORTED_PROTOCOL = "grpc";

    /** Number of fields in the handshake line. */
    private static final int HANDSHAKE_FIELD_COUNT = 5;

    private final Process process;
    private final ManagedChannel channel;
    private final tfplugin5.ProviderGrpc.ProviderBlockingStub stub;
    private final HandshakeResult handshake;

    /**
     * Parsed go-plugin handshake fields.
     *
     * @param coreProtocol core protocol version (must be 1)
     * @param appProtocol  application protocol version (5 or 6)
     * @param networkType  network transport type ({@code "tcp"} or {@code "unix"})
     * @param networkAddr  address to connect to (e.g. {@code "127.0.0.1:54321"})
     * @param protocol     wire protocol (must be {@code "grpc"})
     */
    record HandshakeResult(int coreProtocol, int appProtocol, String networkType, String networkAddr, String protocol) {}

    /**
     * Launch a Terraform provider binary and establish a gRPC connection.
     *
     * @param providerBinaryPath path to the provider binary executable
     * @throws GoPluginException if the binary cannot be started, the handshake fails,
     *                           or the health check does not return SERVING
     */
    public GoPluginClient(Path providerBinaryPath) {
        log.info("Launching go-plugin provider: {}", providerBinaryPath);

        this.process = launchProcess(providerBinaryPath);
        startStderrCapture(process);

        var handshakeLine = readHandshakeLine(process);
        this.handshake = parseHandshake(handshakeLine);
        log.info("Handshake successful: appProtocol={}, networkType={}, addr={}",
                handshake.appProtocol(), handshake.networkType(), handshake.networkAddr());

        var target = buildGrpcTarget(handshake);
        this.channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
        this.stub = tfplugin5.ProviderGrpc.newBlockingStub(channel);

        verifyHealth();
        log.info("Provider is healthy and ready");
    }

    /**
     * Returns the blocking stub for tfplugin5 RPCs.
     *
     * @return the provider blocking stub
     */
    public tfplugin5.ProviderGrpc.ProviderBlockingStub getStub() {
        return stub;
    }

    /**
     * Checks whether the provider process is healthy via gRPC Health Check.
     *
     * @return {@code true} if the health check returns SERVING, {@code false} otherwise
     */
    public boolean isHealthy() {
        try {
            var healthStub = HealthGrpc.newBlockingStub(channel);
            var response = healthStub.check(
                    HealthCheckRequest.newBuilder().setService("plugin").build());
            return response.getStatus() == ServingStatus.SERVING;
        } catch (Exception e) {
            log.warn("Health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Returns the application protocol version from the handshake (5 or 6).
     *
     * @return the app protocol version
     */
    public int getAppProtocolVersion() {
        return handshake.appProtocol();
    }

    /**
     * Gracefully shuts down the provider: sends Stop RPC, closes the gRPC channel,
     * and destroys the subprocess.
     */
    @Override
    public void close() {
        log.info("Shutting down go-plugin provider");

        // 1. Send Stop RPC (best-effort)
        try {
            stub.stop(tfplugin5.Tfplugin5.Stop.Request.getDefaultInstance());
        } catch (Exception e) {
            log.warn("Stop RPC failed (process may have already exited): {}", e.getMessage());
        }

        // 2. Shutdown gRPC channel
        try {
            channel.shutdown();
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("gRPC channel did not terminate in time, forcing shutdown");
                channel.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            channel.shutdownNow();
        }

        // 3. Destroy the process
        if (process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    log.warn("Provider process did not exit gracefully, forcing destruction");
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }

        log.info("Provider shutdown complete");
    }

    // ---------------------------------------------------------------
    // Internal / package-private methods (testable)
    // ---------------------------------------------------------------

    /**
     * Parses a go-plugin handshake line into its component fields.
     *
     * <p>Format: {@code CORE_PROTO|APP_PROTO|NETWORK_TYPE|NETWORK_ADDR|PROTOCOL}
     * <br>Example: {@code 1|5|tcp|127.0.0.1:54321|grpc}</p>
     *
     * @param line the raw handshake line from the provider's stdout
     * @return the parsed handshake result
     * @throws GoPluginException if the line is null, empty, malformed, or contains unsupported values
     */
    static HandshakeResult parseHandshake(String line) {
        if (line == null || line.isBlank()) {
            throw new GoPluginException("Handshake line is null or empty");
        }

        var trimmed = line.trim();
        var parts = trimmed.split("\\|");

        if (parts.length != HANDSHAKE_FIELD_COUNT) {
            throw new GoPluginException(
                    "Invalid handshake format: expected %d fields separated by '|', got %d in line: %s"
                            .formatted(HANDSHAKE_FIELD_COUNT, parts.length, trimmed));
        }

        int coreProtocol;
        try {
            coreProtocol = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            throw new GoPluginException("Invalid core protocol version (not a number): " + parts[0], e);
        }

        if (coreProtocol != SUPPORTED_CORE_PROTOCOL) {
            throw new GoPluginException(
                    "Unsupported core protocol version: %d (expected %d)"
                            .formatted(coreProtocol, SUPPORTED_CORE_PROTOCOL));
        }

        int appProtocol;
        try {
            appProtocol = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new GoPluginException("Invalid app protocol version (not a number): " + parts[1], e);
        }

        var networkType = parts[2];
        var networkAddr = parts[3];
        var protocol = parts[4];

        if (!SUPPORTED_PROTOCOL.equals(protocol)) {
            throw new GoPluginException(
                    "Unsupported protocol: '%s' (only grpc is supported)".formatted(protocol));
        }

        return new HandshakeResult(coreProtocol, appProtocol, networkType, networkAddr, protocol);
    }

    /**
     * Builds the environment variable map for the provider subprocess.
     *
     * @return an unmodifiable map containing the magic cookie entry
     */
    static Map<String, String> buildEnvironment() {
        return Map.of(MAGIC_COOKIE_KEY, MAGIC_COOKIE_VALUE);
    }

    /**
     * Constructs the gRPC channel target string from the handshake result.
     *
     * <p>For TCP, returns the address as-is. For Unix sockets, prepends {@code "unix:"}.</p>
     *
     * @param handshake the parsed handshake result
     * @return the gRPC target string
     */
    static String buildGrpcTarget(HandshakeResult handshake) {
        return switch (handshake.networkType()) {
            case "unix" -> "unix:" + handshake.networkAddr();
            default -> handshake.networkAddr();
        };
    }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    /**
     * Launches the provider binary as a subprocess with the required environment.
     */
    private static Process launchProcess(Path binaryPath) {
        var builder = new ProcessBuilder(binaryPath.toAbsolutePath().toString());
        builder.environment().putAll(buildEnvironment());
        builder.redirectErrorStream(false);

        try {
            return builder.start();
        } catch (IOException e) {
            throw new GoPluginException(
                    "Failed to launch provider binary: " + binaryPath + " — " + e.getMessage(), e);
        }
    }

    /**
     * Reads a single handshake line from the provider process stdout, with a timeout.
     */
    private static String readHandshakeLine(Process process) {
        try {
            var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            // Use a virtual thread to read with a timeout
            var readTask = Thread.ofVirtual().start(() -> {
                // Reading is done in the calling thread via Future below
            });

            var future = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    return reader.readLine();
                } catch (IOException e) {
                    throw new GoPluginException("Failed to read handshake line from provider stdout", e);
                }
            }, java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());

            var line = future.get(HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (line == null) {
                var stderr = captureStderr(process);
                throw new GoPluginException(
                        "Provider process exited before sending handshake line. Stderr: " + stderr);
            }

            return line;
        } catch (GoPluginException e) {
            throw e;
        } catch (java.util.concurrent.TimeoutException e) {
            process.destroyForcibly();
            throw new GoPluginException(
                    "Handshake timeout: provider did not send handshake within %d seconds"
                            .formatted(HANDSHAKE_TIMEOUT_SECONDS));
        } catch (Exception e) {
            throw new GoPluginException("Failed to read handshake line: " + e.getMessage(), e);
        }
    }

    /**
     * Starts a background virtual thread that drains stderr and logs it.
     */
    private static void startStderrCapture(Process process) {
        Thread.ofVirtual().name("go-plugin-stderr").start(() -> {
            try (var reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("[provider stderr] {}", line);
                }
            } catch (IOException e) {
                log.warn("Error reading provider stderr: {}", e.getMessage());
            }
        });
    }

    /**
     * Captures available stderr content from the provider process (best-effort).
     */
    private static String captureStderr(Process process) {
        try (var reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            var sb = new StringBuilder();
            String line;
            while (reader.ready() && (line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.isEmpty() ? "<no stderr output>" : sb.toString().trim();
        } catch (IOException e) {
            return "<failed to read stderr: " + e.getMessage() + ">";
        }
    }

    /**
     * Verifies that the provider is healthy via gRPC Health Check.
     */
    private void verifyHealth() {
        try {
            var healthStub = HealthGrpc.newBlockingStub(channel);
            var response = healthStub.check(
                    HealthCheckRequest.newBuilder().setService("plugin").build());

            if (response.getStatus() != ServingStatus.SERVING) {
                throw new GoPluginException(
                        "Provider health check returned status: " + response.getStatus());
            }
        } catch (GoPluginException e) {
            throw e;
        } catch (Exception e) {
            throw new GoPluginException("Provider health check failed: " + e.getMessage(), e);
        }
    }
}
