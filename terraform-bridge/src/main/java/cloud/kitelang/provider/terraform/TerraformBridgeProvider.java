package cloud.kitelang.provider.terraform;

import cloud.kitelang.provider.KiteProvider;
import lombok.extern.slf4j.Slf4j;

/**
 * Terraform Bridge Provider for Kite.
 *
 * <p>Wraps native Terraform providers (tfplugin5 / tfplugin6) and translates
 * between Kite's gRPC protocol and the Terraform plugin protocol, allowing
 * any existing Terraform provider to be used as a Kite provider.</p>
 *
 * <p>Auto-discovery is disabled because resource types are dynamically
 * populated at runtime from the wrapped Terraform provider's schema.</p>
 */
@Slf4j
public class TerraformBridgeProvider extends KiteProvider {

    public TerraformBridgeProvider() {
        super(false); // disable auto-discovery; resources come from Terraform schema
        log.info("Terraform Bridge Provider initialized");
    }
}
