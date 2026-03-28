package cloud.kitelang.provider.gcp;

import com.google.cloud.compute.v1.InstancesClient;
import com.google.cloud.compute.v1.NetworksClient;
import com.google.cloud.compute.v1.SubnetworksClient;
import com.google.cloud.compute.v1.ForwardingRulesClient;
import com.google.cloud.dns.Dns;
import com.google.cloud.storage.Storage;

/**
 * Interface for resource type handlers that need GCP SDK clients.
 * Implemented by resource types so the provider can push configured clients
 * (with the correct project and credentials) after {@code configure()} is called.
 *
 * <p>Each handler picks the client it needs from the arguments and ignores the rest.</p>
 */
public interface GcpClientAware {

    /**
     * Receive configured GCP clients from the provider.
     * Implementations should store only the client type they actually use.
     *
     * @param instancesClient       configured Compute Engine instances client
     * @param networksClient        configured Compute Engine networks client
     * @param subnetworksClient     configured Compute Engine subnetworks client
     * @param forwardingRulesClient configured Compute Engine forwarding rules client
     * @param storage               configured Cloud Storage client
     * @param dns                   configured Cloud DNS client
     */
    void setGcpClients(InstancesClient instancesClient,
                       NetworksClient networksClient,
                       SubnetworksClient subnetworksClient,
                       ForwardingRulesClient forwardingRulesClient,
                       Storage storage,
                       Dns dns);
}
