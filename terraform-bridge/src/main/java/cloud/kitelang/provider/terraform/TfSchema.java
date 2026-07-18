package cloud.kitelang.provider.terraform;

/**
 * Protocol-neutral Terraform resource/data-source/provider schema.
 *
 * <p>Both tfplugin5 and tfplugin6 define a structurally similar {@code Schema}
 * message; the protocol adapters ({@link Tfplugin5Rpc}, {@link Tfplugin6Rpc})
 * convert their generated classes into this shared model so schema consumers
 * ({@link TerraformSchemaConverter}, {@link TerraformBridgeProvider}) stay
 * version-agnostic.</p>
 *
 * @param block the top-level configuration block
 */
public record TfSchema(TfBlock block) {
}
