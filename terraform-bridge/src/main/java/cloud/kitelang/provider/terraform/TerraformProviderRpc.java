package cloud.kitelang.provider.terraform;

import java.util.List;
import java.util.Map;

/**
 * Version-agnostic facade over the Terraform plugin protocol RPCs the bridge uses.
 *
 * <p>tfplugin5 and tfplugin6 carry structurally identical request/response
 * messages for these operations but name several RPCs differently:</p>
 *
 * <table>
 *   <tr><th>Operation</th><th>tfplugin5 RPC</th><th>tfplugin6 RPC</th></tr>
 *   <tr><td>{@link #getProviderSchema()}</td><td>{@code GetSchema}</td><td>{@code GetProviderSchema}</td></tr>
 *   <tr><td>{@link #validateProviderConfig(byte[])}</td><td>{@code PrepareProviderConfig}</td><td>{@code ValidateProviderConfig}</td></tr>
 *   <tr><td>{@link #configure(byte[])}</td><td>{@code Configure}</td><td>{@code ConfigureProvider}</td></tr>
 *   <tr><td>{@link #validateResourceConfig}</td><td>{@code ValidateResourceTypeConfig}</td><td>{@code ValidateResourceConfig}</td></tr>
 *   <tr><td>{@link #planResourceChange}</td><td colspan=2>{@code PlanResourceChange}</td></tr>
 *   <tr><td>{@link #applyResourceChange}</td><td colspan=2>{@code ApplyResourceChange}</td></tr>
 *   <tr><td>{@link #readResource}</td><td colspan=2>{@code ReadResource}</td></tr>
 *   <tr><td>{@link #readDataSource}</td><td colspan=2>{@code ReadDataSource}</td></tr>
 *   <tr><td>{@link #stop()}</td><td>{@code Stop}</td><td>{@code StopProvider}</td></tr>
 * </table>
 *
 * <p>All state, config, and plan payloads are cty msgpack byte arrays (the
 * {@code DynamicValue.msgpack} field) as produced/consumed by {@link CtyCodec}.
 * A cty null value is the msgpack nil encoding, never an empty array. Private
 * bytes are opaque provider state; empty arrays mean "none".</p>
 *
 * <p>The implementation is selected by {@link GoPluginClient} from the app
 * protocol version negotiated in the go-plugin handshake — no user configuration.</p>
 *
 * @see Tfplugin5Rpc
 * @see Tfplugin6Rpc
 */
public interface TerraformProviderRpc {

    /**
     * Fetches the provider's full schema.
     *
     * @return the provider, resource, and data source schemas in neutral form
     */
    ProviderSchema getProviderSchema();

    /**
     * Validates the provider configuration before {@link #configure(byte[])}.
     *
     * <p>tfplugin5's {@code PrepareProviderConfig} may additionally rewrite the
     * config (legacy SDKs fill defaults there); the returned prepared config is
     * what must be sent to Configure. tfplugin6 dropped the prepare semantics,
     * so its implementation passes the input config through unchanged.</p>
     *
     * @param configMsgpack cty msgpack-encoded provider configuration
     * @return the config to send to Configure plus the response diagnostics
     */
    ProviderConfigValidation validateProviderConfig(byte[] configMsgpack);

    /**
     * Configures the provider with runtime credentials/settings.
     *
     * @param configMsgpack cty msgpack-encoded provider configuration
     * @return the response diagnostics
     */
    List<TfDiagnostic> configure(byte[] configMsgpack);

    /**
     * Validates a resource configuration.
     *
     * @param typeName      the TF resource type name (e.g. {@code "aws_instance"})
     * @param configMsgpack cty msgpack-encoded configuration
     * @return the response diagnostics
     */
    List<TfDiagnostic> validateResourceConfig(String typeName, byte[] configMsgpack);

    /**
     * Plans a resource change.
     *
     * @param typeName         the TF resource type name
     * @param priorState       cty msgpack prior state (nil for create)
     * @param proposedNewState cty msgpack desired state (nil for destroy)
     * @param config           cty msgpack configuration (nil for destroy)
     * @param priorPrivate     provider-private bytes recorded at the last apply
     * @return the plan result
     */
    PlanResult planResourceChange(String typeName, byte[] priorState, byte[] proposedNewState,
                                  byte[] config, byte[] priorPrivate);

    /**
     * Applies a planned resource change.
     *
     * @param typeName       the TF resource type name
     * @param priorState     cty msgpack prior state
     * @param plannedState   the plan's planned state (relayed verbatim)
     * @param config         cty msgpack configuration (nil for destroy)
     * @param plannedPrivate the plan's private bytes (relayed verbatim)
     * @return the new state, private bytes, and diagnostics
     */
    StateResult applyResourceChange(String typeName, byte[] priorState, byte[] plannedState,
                                    byte[] config, byte[] plannedPrivate);

    /**
     * Reads the current state of a resource.
     *
     * @param typeName     the TF resource type name
     * @param currentState cty msgpack last known state
     * @param privateBytes provider-private bytes recorded at the last apply
     * @return the refreshed state, private bytes, and diagnostics
     */
    StateResult readResource(String typeName, byte[] currentState, byte[] privateBytes);

    /**
     * Reads a data source.
     *
     * @param typeName      the TF data source type name (e.g. {@code "aws_ami"})
     * @param configMsgpack cty msgpack-encoded query configuration
     * @return the data source state and diagnostics
     */
    DataSourceResult readDataSource(String typeName, byte[] configMsgpack);

    /** Asks the provider process to shut down gracefully (best-effort). */
    void stop();

    /**
     * Neutral {@code GetProviderSchema} response.
     *
     * @param provider          the provider config schema, or null when absent
     * @param resourceSchemas   resource schemas keyed by TF type name
     * @param dataSourceSchemas data source schemas keyed by TF type name
     */
    record ProviderSchema(TfSchema provider,
                          Map<String, TfSchema> resourceSchemas,
                          Map<String, TfSchema> dataSourceSchemas) {

        public ProviderSchema {
            resourceSchemas = Map.copyOf(resourceSchemas);
            dataSourceSchemas = Map.copyOf(dataSourceSchemas);
        }
    }

    /**
     * Neutral provider-config validation response.
     *
     * @param preparedConfig cty msgpack config to send to Configure — tfplugin5's
     *                       prepared rewrite when present, otherwise the input config
     * @param diagnostics    the response diagnostics
     */
    record ProviderConfigValidation(byte[] preparedConfig, List<TfDiagnostic> diagnostics) {
    }

    /**
     * Neutral {@code PlanResourceChange} response.
     *
     * @param plannedState    cty msgpack planned state (relay to the apply)
     * @param plannedPrivate  private bytes to relay to the apply
     * @param requiresReplace attribute paths whose change forces destroy-and-recreate
     * @param diagnostics     the response diagnostics
     */
    record PlanResult(byte[] plannedState, byte[] plannedPrivate,
                      List<TfAttributePath> requiresReplace, List<TfDiagnostic> diagnostics) {
    }

    /**
     * Neutral apply/read response: the resource state after the operation.
     *
     * @param state        cty msgpack resource state
     * @param privateBytes provider-private bytes to persist for the next operation
     * @param diagnostics  the response diagnostics
     */
    record StateResult(byte[] state, byte[] privateBytes, List<TfDiagnostic> diagnostics) {
    }

    /**
     * Neutral {@code ReadDataSource} response.
     *
     * @param state       cty msgpack data source state
     * @param diagnostics the response diagnostics
     */
    record DataSourceResult(byte[] state, List<TfDiagnostic> diagnostics) {
    }
}
