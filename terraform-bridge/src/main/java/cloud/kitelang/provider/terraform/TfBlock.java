package cloud.kitelang.provider.terraform;

import java.util.List;

/**
 * Protocol-neutral schema block: a set of attributes plus nested blocks.
 *
 * @param attributes the block's attribute definitions, in schema order
 * @param blockTypes the block's nested block definitions, in schema order
 */
public record TfBlock(List<TfAttribute> attributes, List<TfNestedBlock> blockTypes) {

    public TfBlock {
        attributes = List.copyOf(attributes);
        blockTypes = List.copyOf(blockTypes);
    }
}
