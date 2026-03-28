package cloud.kitelang.provider.azure;

import cloud.kitelang.provider.KiteProvider;
import cloud.kitelang.provider.ProviderServer;
import cloud.kitelang.provider.azure.compute.VirtualMachineResourceType;
import cloud.kitelang.provider.azure.dns.DnsRecordSetResourceType;
import cloud.kitelang.provider.azure.dns.DnsZoneResourceType;
import cloud.kitelang.provider.azure.loadbalancing.LoadBalancerResourceType;
import cloud.kitelang.provider.azure.networking.SubnetResourceType;
import cloud.kitelang.provider.azure.networking.VnetResourceType;
import cloud.kitelang.provider.azure.stdlib.*;
import cloud.kitelang.provider.azure.storage.StorageAccountResourceType;
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
        // Resource types are discovered by the superclass constructor
        registerStandardTypeAdapters();
        log.info("Azure Provider initialized with resources: {}", getResourceTypes().keySet());
    }

    /**
     * Register standard library type adapters for all supported mappings.
     * Each adapter bridges a provider-agnostic stdlib type to its Azure-specific implementation.
     * Must be called after resource types are discovered (handled by superclass constructor).
     */
    private void registerStandardTypeAdapters() {
        var vmHandler = getResourceTypes().get("VirtualMachine");
        if (vmHandler instanceof VirtualMachineResourceType typed) {
            registerAdapter(new ServerAdapter(typed));
        }

        var vnetHandler = getResourceTypes().get("Vnet");
        if (vnetHandler instanceof VnetResourceType typed) {
            registerAdapter(new NetworkAdapter(typed));
        }

        var subnetHandler = getResourceTypes().get("Subnet");
        if (subnetHandler instanceof SubnetResourceType typed) {
            registerAdapter(new SubnetAdapter(typed));
        }

        var storageHandler = getResourceTypes().get("StorageAccount");
        if (storageHandler instanceof StorageAccountResourceType typed) {
            registerAdapter(new BucketAdapter(typed));
        }

        var lbHandler = getResourceTypes().get("LoadBalancer");
        if (lbHandler instanceof LoadBalancerResourceType typed) {
            registerAdapter(new LoadBalancerAdapter(typed));
        }

        var dnsZoneHandler = getResourceTypes().get("DnsZone");
        if (dnsZoneHandler instanceof DnsZoneResourceType typed) {
            registerAdapter(new DnsZoneAdapter(typed));
        }

        var dnsRecordHandler = getResourceTypes().get("DnsRecordSet");
        if (dnsRecordHandler instanceof DnsRecordSetResourceType typed) {
            registerAdapter(new DnsRecordAdapter(typed));
        }

        log.info("Registered {} standard type adapters", getStandardTypeAdapters().size());
    }

    public static void main(String[] args) throws Exception {
        log.info("Starting Azure Provider...");
        ProviderServer.serve(new AzureProvider());
    }
}
