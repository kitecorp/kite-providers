package cloud.kitelang.provider.distribution;

import cloud.kitelang.proto.v1.GetProviderSchema;
import cloud.kitelang.proto.v1.ProviderGrpc;
import cloud.kitelang.proto.v1.StopProvider;
import io.grpc.ManagedChannelBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * What a provider distribution served when started the way the engine starts it:
 * launcher process, stdout handshake, then the provider gRPC protocol.
 *
 * @param resourceTypes the resource type names GetProviderSchema returned
 * @param log           every line the provider wrote, stderr and stdout, apart from the handshake
 */
record ProviderRun(SortedSet<String> resourceTypes, List<String> log) {

    private static final String HANDSHAKE_PREFIX = "KITE_PLUGIN|";
    private static final Pattern HANDSHAKE = Pattern.compile("KITE_PLUGIN\\|1\\|(\\d+)\\|grpc");
    // A cold JVM loading a cloud SDK on a shared CI runner can take tens of seconds
    private static final long HANDSHAKE_TIMEOUT_SECONDS = 60;
    private static final long CALL_DEADLINE_SECONDS = 30;
    private static final long EXIT_TIMEOUT_SECONDS = 30;

    /**
     * Starts the distribution, asks it for its schema and stops it again.
     *
     * @throws IllegalStateException when the provider does not complete the handshake or
     *         does not answer; the message includes the provider's log
     */
    static ProviderRun start(ProviderDistribution distribution) throws IOException, InterruptedException {
        var process = launch(distribution);
        var log = new CopyOnWriteArrayList<String>();
        var logReader = Thread.ofPlatform().daemon().start(() -> readLines(process.getErrorStream(), log::add));
        try {
            var resourceTypes = askForResourceTypes(awaitHandshake(process, log::add));
            awaitExit(process, logReader);
            return new ProviderRun(resourceTypes, List.copyOf(log));
        } catch (RuntimeException | ExecutionException | TimeoutException e) {
            process.destroyForcibly();
            awaitExit(process, logReader);
            throw new IllegalStateException(
                    "Provider " + distribution + " failed: " + e + "\nProvider log:\n" + String.join("\n", log), e);
        } finally {
            // An interrupt or an Error skips the catch; the provider must not outlive the test
            process.destroyForcibly();
        }
    }

    private static Process launch(ProviderDistribution distribution) throws IOException {
        var builder = new ProcessBuilder(distribution.executable().toString())
                .directory(distribution.directory().toFile());
        var environment = builder.environment();
        // The engine hands a provider whatever environment the user has. Starting it with
        // no cloud credentials or settings catches, on any machine, a handler that cannot be
        // created without them: every azure 0.2.0 handler needed AZURE_SUBSCRIPTION_ID.
        environment.clear();
        environment.put("PATH", System.getenv("PATH"));
        // The JDK running this test: Java 25, the oldest a provider supports
        environment.put("JAVA_HOME", System.getProperty("java.home"));
        environment.put("KITE_PLUGIN_MAGIC_COOKIE", UUID.randomUUID().toString());
        environment.put("KITE_PLUGIN_PROTOCOL_VERSION", "1");
        // Once serving, a provider whose test JVM was killed stops by itself
        environment.put("KITE_PLUGIN_IDLE_TIMEOUT", String.valueOf(TimeUnit.MINUTES.toMillis(2)));
        return builder.start();
    }

    /**
     * Returns the port from the handshake line the provider prints on stdout once it listens,
     * passing every other stdout line to {@code otherLines}.
     */
    private static int awaitHandshake(Process process, Consumer<String> otherLines)
            throws InterruptedException, ExecutionException, TimeoutException {
        var handshake = new CompletableFuture<String>();
        // Keeps reading after the handshake so the provider never blocks on a full pipe
        Thread.ofPlatform().daemon().start(() -> {
            readLines(process.getInputStream(), line -> {
                if (!handshake.isDone() && line.startsWith(HANDSHAKE_PREFIX)) {
                    handshake.complete(line);
                } else {
                    otherLines.accept(line);
                }
            });
            handshake.complete(null);
        });
        var line = handshake.get(HANDSHAKE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (line == null) {
            throw new IllegalStateException("closed stdout without a handshake");
        }
        var matcher = HANDSHAKE.matcher(line);
        if (!matcher.matches()) {
            throw new IllegalStateException("unexpected handshake '" + line + "'");
        }
        return Integer.parseInt(matcher.group(1));
    }

    /** Asks for the schema, then stops the provider the way the engine does. */
    private static SortedSet<String> askForResourceTypes(int port) {
        var channel = ManagedChannelBuilder.forAddress("127.0.0.1", port).usePlaintext().build();
        try {
            var provider = ProviderGrpc.newBlockingStub(channel);
            var schema = provider.withDeadlineAfter(CALL_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .getProviderSchema(GetProviderSchema.Request.getDefaultInstance());
            provider.withDeadlineAfter(CALL_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .stopProvider(StopProvider.Request.getDefaultInstance());
            return new TreeSet<>(schema.getResourceSchemasMap().keySet());
        } finally {
            channel.shutdownNow();
        }
    }

    /** Waits for the provider to exit, killing it if it does not, and for its whole log. */
    private static void awaitExit(Process process, Thread logReader) throws InterruptedException {
        if (!process.waitFor(EXIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly().waitFor(EXIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        logReader.join(TimeUnit.SECONDS.toMillis(EXIT_TIMEOUT_SECONDS));
    }

    private static void readLines(InputStream stream, Consumer<String> lines) {
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            reader.lines().forEach(lines);
        } catch (IOException | UncheckedIOException e) {
            // The stream closes when the process exits; what was read so far is all there is
        }
    }
}
