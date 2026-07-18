package cloud.kitelang.provider.terraform;

/**
 * Protocol-neutral schema attribute.
 *
 * <p>The cty type is carried as its JSON encoding ({@code typeJson}, e.g.
 * {@code "string"} or {@code ["list","number"]}) — the same representation both
 * protocols put in the {@code Schema.Attribute.type} bytes. tfplugin6 attributes
 * defined via {@code nested_type} (nested attributes, absent from tfplugin5) are
 * translated by {@link Tfplugin6Rpc} into the equivalent cty object type JSON,
 * which is exactly how the values are encoded on the wire, so {@link CtyCodec}
 * round-trips them without protocol-specific handling.</p>
 *
 * @param name      snake_case attribute name
 * @param typeJson  JSON-encoded cty type of the attribute's value
 * @param required  the practitioner must set a value
 * @param optional  the practitioner may set a value
 * @param computed  the provider fills in a value
 * @param sensitive the value must not be displayed
 * @param writeOnly the value is accepted on writes but never stored in state
 */
public record TfAttribute(String name, String typeJson, boolean required, boolean optional,
                          boolean computed, boolean sensitive, boolean writeOnly) {
}
