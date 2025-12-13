package cloud.kitelang.provider.azure;

import cloud.kitelang.provider.KiteProvider;
import cloud.kitelang.provider.ProviderServer;
import lombok.extern.log4j.Log4j2;

/**
 * Azure Provider for Kite.
 *
 * Provides resources for managing Azure infrastructure:
 * - ResourceGroup: Container for Azure resources
 * - Vnet: Virtual Network (equivalent to AWS VPC)
 *
 * Authentication uses Azure Default Credential Chain:
 * 1. Environment variables (AZURE_CLIENT_ID, AZURE_CLIENT_SECRET, AZURE_TENANT_ID)
 * 2. Managed Identity (when running in Azure)
 * 3. Azure CLI credentials (az login)
 * 4. Visual Studio Code credentials
 *
 * Required environment variable:
 * - AZURE_SUBSCRIPTION_ID: Your Azure subscription ID
 *
 * Example usage in Kite:
 * <pre>
 * resource ResourceGroup main {
 *     name = "my-resource-group"
 *     location = "eastus"
 *     tags = {
 *         Environment: "production"
 *     }
 * }
 *
 * resource Vnet network {
 *     name = "main-vnet"
 *     resourceGroup = main.name
 *     location = main.location
 *     addressSpaces = ["10.0.0.0/16"]
 *     tags = {
 *         Environment: "production"
 *     }
 * }
 * </pre>
 */
@Log4j2
public class AzureProvider extends KiteProvider {

    public AzureProvider() {
        super("azure", "0.1.0");
        // Auto-discover all ResourceTypeHandler classes in this package
        discoverResources();
        log.info("Azure Provider initialized with resources: {}", getResourceTypes().keySet());
    }

    public static void main(String[] args) throws Exception {
        log.info("Starting Azure Provider...");
        ProviderServer.serve(new AzureProvider());
    }
}
