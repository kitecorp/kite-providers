package cloud.kitelang.providers.files;

import cloud.kitelang.provider.KiteProvider;
import cloud.kitelang.provider.ProviderServer;
import lombok.extern.log4j.Log4j2;

/**
 * Example gRPC provider demonstrating the Kite Provider SDK.
 *
 * <p>This provider manages local files and serves as a reference implementation
 * for creating gRPC-based Kite providers.</p>
 *
 * <h2>Usage</h2>
 * <p>The provider is launched by the Kite engine. It should not be run directly.
 * When launched, it performs a handshake with the engine and starts a gRPC server.</p>
 *
 * <h2>Resource Types</h2>
 * <ul>
 *   <li>{@link FileResource} - Manages local files with content and permissions</li>
 * </ul>
 */
@Log4j2
public class ExampleProvider extends KiteProvider {

    public ExampleProvider() {
        super("cloud/kitelang/providers/files", "0.1.0");
        log.info("Example provider initialized with {} resource types", getResourceTypes().size());
    }

    @Override
    public void stop() {
        log.info("Example provider shutting down");
        super.stop();
    }

    /**
     * Main entry point.
     * This method is called when the provider is launched by the Kite engine.
     */
    public static void main(String[] args) {
        try {
            ProviderServer.serve(new ExampleProvider());
        } catch (Exception e) {
            log.error("Failed to start provider", e);
            System.exit(1);
        }
    }
}
