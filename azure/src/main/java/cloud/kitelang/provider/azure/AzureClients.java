package cloud.kitelang.provider.azure;

import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.compute.ComputeManager;
import com.azure.resourcemanager.dns.DnsZoneManager;
import com.azure.resourcemanager.network.NetworkManager;
import com.azure.resourcemanager.resources.ResourceManager;
import com.azure.resourcemanager.storage.StorageManager;
import lombok.extern.slf4j.Slf4j;

/**
 * Lazy, shared accessors for Azure SDK managers.
 *
 * <p>Authentication happens the first time any manager is requested, not at
 * ResourceType construction. This matters because {@code KiteProvider} instantiates
 * every {@code ResourceTypeHandler} at startup (and at docgen time) via reflection
 * — constructing a manager in the no-arg constructor would require Azure
 * credentials to be present in every environment, including CI docgen, which
 * breaks documentation generation.</p>
 *
 * <p>Each manager type is cached as a volatile singleton using double-checked
 * locking. All managers share the same {@link AzureProfile} and
 * {@code DefaultAzureCredential}.</p>
 *
 * <p>Required environment:</p>
 * <ul>
 *   <li>{@code AZURE_SUBSCRIPTION_ID} — mandatory</li>
 *   <li>{@code AZURE_TENANT_ID} — optional, used by some credential chains</li>
 * </ul>
 *
 * <p>Credentials resolved via {@link DefaultAzureCredentialBuilder} in this order:
 * env vars ({@code AZURE_CLIENT_ID}/{@code AZURE_CLIENT_SECRET}/{@code AZURE_TENANT_ID}),
 * Managed Identity, Azure CLI, Visual Studio Code.</p>
 */
@Slf4j
public final class AzureClients {

    private static volatile AzureProfile profile;
    private static volatile DefaultAzureCredentialBuilder credentialBuilder;

    private static volatile ResourceManager resourceManager;
    private static volatile NetworkManager networkManager;
    private static volatile ComputeManager computeManager;
    private static volatile StorageManager storageManager;
    private static volatile DnsZoneManager dnsZoneManager;

    private AzureClients() {
        // utility class
    }

    /**
     * Returns the configured {@link AzureProfile}, constructing it on first use.
     *
     * @throws IllegalStateException if {@code AZURE_SUBSCRIPTION_ID} is not set
     */
    private static AzureProfile profile() {
        var local = profile;
        if (local == null) {
            synchronized (AzureClients.class) {
                local = profile;
                if (local == null) {
                    String subscriptionId = System.getenv("AZURE_SUBSCRIPTION_ID");
                    if (subscriptionId == null || subscriptionId.isBlank()) {
                        throw new IllegalStateException(
                                "AZURE_SUBSCRIPTION_ID environment variable must be set");
                    }
                    String tenantId = System.getenv("AZURE_TENANT_ID");
                    local = new AzureProfile(tenantId, subscriptionId, AzureEnvironment.AZURE);
                    profile = local;
                    log.info("Initialized Azure profile for subscription {}", subscriptionId);
                }
            }
        }
        return local;
    }

    private static DefaultAzureCredentialBuilder credentialBuilder() {
        var local = credentialBuilder;
        if (local == null) {
            synchronized (AzureClients.class) {
                local = credentialBuilder;
                if (local == null) {
                    local = new DefaultAzureCredentialBuilder();
                    credentialBuilder = local;
                }
            }
        }
        return local;
    }

    /** Lazily builds and returns the shared {@link ResourceManager}. */
    public static ResourceManager resource() {
        var local = resourceManager;
        if (local == null) {
            synchronized (AzureClients.class) {
                local = resourceManager;
                if (local == null) {
                    local = ResourceManager.authenticate(credentialBuilder().build(), profile())
                            .withDefaultSubscription();
                    resourceManager = local;
                }
            }
        }
        return local;
    }

    /** Lazily builds and returns the shared {@link NetworkManager}. */
    public static NetworkManager network() {
        var local = networkManager;
        if (local == null) {
            synchronized (AzureClients.class) {
                local = networkManager;
                if (local == null) {
                    local = NetworkManager.authenticate(credentialBuilder().build(), profile());
                    networkManager = local;
                }
            }
        }
        return local;
    }

    /** Lazily builds and returns the shared {@link ComputeManager}. */
    public static ComputeManager compute() {
        var local = computeManager;
        if (local == null) {
            synchronized (AzureClients.class) {
                local = computeManager;
                if (local == null) {
                    local = ComputeManager.authenticate(credentialBuilder().build(), profile());
                    computeManager = local;
                }
            }
        }
        return local;
    }

    /** Lazily builds and returns the shared {@link StorageManager}. */
    public static StorageManager storage() {
        var local = storageManager;
        if (local == null) {
            synchronized (AzureClients.class) {
                local = storageManager;
                if (local == null) {
                    local = StorageManager.authenticate(credentialBuilder().build(), profile());
                    storageManager = local;
                }
            }
        }
        return local;
    }

    /** Lazily builds and returns the shared {@link DnsZoneManager}. */
    public static DnsZoneManager dnsZone() {
        var local = dnsZoneManager;
        if (local == null) {
            synchronized (AzureClients.class) {
                local = dnsZoneManager;
                if (local == null) {
                    local = DnsZoneManager.authenticate(credentialBuilder().build(), profile());
                    dnsZoneManager = local;
                }
            }
        }
        return local;
    }
}
