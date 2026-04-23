package cloud.kitelang.provider.terraform;

import cloud.kitelang.provider.ProviderServer;
import lombok.extern.slf4j.Slf4j;

/**
 * Entry point for the Terraform Bridge Provider.
 *
 * <p>Starts the gRPC server that bridges Kite's provider protocol
 * to Terraform's tfplugin5/tfplugin6 protocol.</p>
 */
@Slf4j
public class Main {

    public static void main(String[] args) throws Exception {
        log.info("Starting Terraform Bridge Provider...");
        var provider = new TerraformBridgeProvider();
        provider.init();
        ProviderServer.serve(provider);
    }
}
