package cloud.kitelang.providers.files;

import cloud.kitelang.provider.KiteProvider;
import cloud.kitelang.provider.ProviderServer;
import lombok.extern.log4j.Log4j2;

/**
 * Files provider for managing local files.
 *
 * <h2>Resource Types</h2>
 * <ul>
 *   <li>{@link FileResource} - Manages local files with content and permissions</li>
 * </ul>
 */
@Log4j2
public class FilesProvider extends KiteProvider {

    public FilesProvider() {
        // Name and version auto-loaded from provider-info.properties
        log.info("Files provider initialized");
    }

    @Override
    public void stop() {
        log.info("Files provider shutting down");
        super.stop();
    }

    public static void main(String[] args) {
        try {
            ProviderServer.serve(new FilesProvider());
        } catch (Exception e) {
            log.error("Failed to start provider", e);
            System.exit(1);
        }
    }
}
