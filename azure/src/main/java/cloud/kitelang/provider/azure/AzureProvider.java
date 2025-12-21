package cloud.kitelang.provider.azure;

import cloud.kitelang.provider.KiteProvider;
import cloud.kitelang.provider.ProviderServer;
import lombok.extern.slf4j.Slf4j;

/**
 * Azure Provider for Kite.
 *
 * Provides resources for managing Azure infrastructure:
 * - ResourceGroup: Container for Azure resources
 * - Vnet: Virtual Network (equivalent to AWS VPC)
 * - NetworkSecurityGroup: Firewall rules (equivalent to AWS Security Group)
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
@Slf4j
public class AzureProvider extends KiteProvider {

    public AzureProvider() {
        // Name and version auto-loaded from provider-info.properties
        log.info("Azure Provider initialized with resources: {}", getResourceTypes().keySet());
    }

    public static void main(String[] args) throws Exception {
        log.info("Starting Azure Provider...");
        ProviderServer.serve(new AzureProvider());
    }
}
