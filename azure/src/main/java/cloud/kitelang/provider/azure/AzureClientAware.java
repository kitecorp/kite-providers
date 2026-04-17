package cloud.kitelang.provider.azure;

/**
 * Interface for Azure resource type handlers that need SDK managers.
 * Implemented by resource types so the provider can push configured managers
 * after {@code configure()} is called.
 *
 * <p>Each handler picks the manager(s) it needs from {@link AzureManagers}
 * and ignores the rest.</p>
 *
 * @see AzureProvider#configure(Object)
 */
public interface AzureClientAware {

    /**
     * Receive configured Azure SDK managers from the provider.
     * Implementations should store only the manager type(s) they actually use.
     */
    void setAzureManagers(AzureManagers managers);
}
