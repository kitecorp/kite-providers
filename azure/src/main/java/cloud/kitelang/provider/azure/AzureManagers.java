package cloud.kitelang.provider.azure;

import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.dns.DnsZoneManager;
import com.azure.resourcemanager.network.NetworkManager;
import com.azure.resourcemanager.resources.ResourceManager;
import com.azure.resourcemanager.storage.StorageManager;

/**
 * Bundles all Azure SDK managers into a single immutable carrier.
 * Passed to resource type handlers via {@link AzureClientAware#setAzureManagers(AzureManagers)}.
 *
 * <p>Each handler extracts only the manager(s) it needs.</p>
 */
public record AzureManagers(
        ResourceManager resource,
        NetworkManager network,
        ComputeManager compute,
        StorageManager storage,
        DnsZoneManager dnsZone
) {}
