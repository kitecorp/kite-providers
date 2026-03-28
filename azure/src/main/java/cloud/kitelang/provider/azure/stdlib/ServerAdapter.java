package cloud.kitelang.provider.azure.stdlib;

import cloud.kitelang.provider.ResourceTypeHandler;
import cloud.kitelang.provider.StandardTypeAdapter;
import cloud.kitelang.provider.azure.compute.VirtualMachineResource;
import cloud.kitelang.provider.azure.compute.VirtualMachineResourceType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapts the standard library {@code Server} type to Azure {@code VirtualMachine}.
 *
 * <p>Property mapping:</p>
 * <ul>
 *   <li>{@code cpu} + {@code memory} -> {@code size} (via Azure VM size lookup table)</li>
 *   <li>{@code image} -> {@code imagePublisher/imageOffer/imageSku} (parsed from "publisher:offer:sku" format)</li>
 *   <li>{@code name} -> {@code name}</li>
 *   <li>{@code region} -> {@code location}</li>
 *   <li>{@code tags} -> {@code tags}</li>
 * </ul>
 *
 * <p>Cloud-managed properties mapped back:</p>
 * <ul>
 *   <li>{@code publicIp} <- {@code publicIp}</li>
 *   <li>{@code privateIp} <- {@code privateIp}</li>
 *   <li>{@code id} <- {@code id}</li>
 *   <li>{@code state} <- {@code powerState}</li>
 * </ul>
 */
public class ServerAdapter implements StandardTypeAdapter<VirtualMachineResource> {

    /**
     * Lookup table mapping (cpu, memory) pairs to Azure VM sizes.
     * Key format: "cpu:memory" (both in integer form).
     * Entries are ordered from smallest to largest for closest-match fallback.
     */
    private static final LinkedHashMap<String, String> VM_SIZE_TABLE = new LinkedHashMap<>();

    static {
        VM_SIZE_TABLE.put("1:1", "Standard_B1s");
        VM_SIZE_TABLE.put("1:2", "Standard_B1ms");
        VM_SIZE_TABLE.put("2:4", "Standard_B2s");
        VM_SIZE_TABLE.put("2:8", "Standard_B2ms");
        VM_SIZE_TABLE.put("4:16", "Standard_B4ms");
        VM_SIZE_TABLE.put("8:32", "Standard_B8ms");
    }

    /**
     * Sorted entries for closest-match lookup, ordered by total capacity (cpu * memory).
     */
    private record VmSpec(int cpu, int memory, String size) {}

    private static final VmSpec[] SORTED_SPECS = {
            new VmSpec(1, 1, "Standard_B1s"),
            new VmSpec(1, 2, "Standard_B1ms"),
            new VmSpec(2, 4, "Standard_B2s"),
            new VmSpec(2, 8, "Standard_B2ms"),
            new VmSpec(4, 16, "Standard_B4ms"),
            new VmSpec(8, 32, "Standard_B8ms"),
    };

    /**
     * Default image reference used when no image is specified.
     * Ubuntu 22.04 LTS Gen2 from Canonical.
     */
    private static final String DEFAULT_IMAGE_PUBLISHER = "Canonical";
    private static final String DEFAULT_IMAGE_OFFER = "0001-com-ubuntu-server-jammy";
    private static final String DEFAULT_IMAGE_SKU = "22_04-lts-gen2";

    private final VirtualMachineResourceType handler;

    public ServerAdapter(VirtualMachineResourceType handler) {
        this.handler = handler;
    }

    @Override
    public String standardTypeName() {
        return "Server";
    }

    @Override
    public String concreteTypeName() {
        return "VirtualMachine";
    }

    @Override
    @SuppressWarnings("unchecked")
    public VirtualMachineResource toConcreteProperties(Map<String, Object> abstractProps) {
        var builder = VirtualMachineResource.builder();

        // Map name -> name
        var name = (String) abstractProps.get("name");
        if (name != null) {
            builder.name(name);
        }

        // Map cpu + memory -> size
        var cpu = toInt(abstractProps.get("cpu"));
        var memory = toInt(abstractProps.get("memory"));
        builder.size(resolveVmSize(cpu, memory));

        // Map region -> location
        var region = (String) abstractProps.get("region");
        if (region != null) {
            builder.location(region);
        }

        // Map image -> imagePublisher/imageOffer/imageSku
        // Accepted format: "publisher:offer:sku" or just stored as-is for the SKU
        var image = (String) abstractProps.get("image");
        if (image != null) {
            var parts = image.split(":");
            if (parts.length >= 3) {
                builder.imagePublisher(parts[0]);
                builder.imageOffer(parts[1]);
                builder.imageSku(parts[2]);
            } else {
                // Single value: use defaults for publisher/offer, treat as SKU
                builder.imagePublisher(DEFAULT_IMAGE_PUBLISHER);
                builder.imageOffer(DEFAULT_IMAGE_OFFER);
                builder.imageSku(image);
            }
        } else {
            builder.imagePublisher(DEFAULT_IMAGE_PUBLISHER);
            builder.imageOffer(DEFAULT_IMAGE_OFFER);
            builder.imageSku(DEFAULT_IMAGE_SKU);
        }

        // Map tags directly
        var abstractTags = abstractProps.get("tags");
        if (abstractTags instanceof Map<?, ?> rawTags) {
            var tags = new HashMap<String, String>();
            rawTags.forEach((k, v) -> tags.put(String.valueOf(k), String.valueOf(v)));
            builder.tags(tags);
        }

        return builder.build();
    }

    @Override
    public Map<String, Object> toAbstractProperties(VirtualMachineResource concrete) {
        var result = new HashMap<String, Object>();

        if (concrete.getPublicIp() != null) {
            result.put("publicIp", concrete.getPublicIp());
        }
        if (concrete.getPrivateIp() != null) {
            result.put("privateIp", concrete.getPrivateIp());
        }
        if (concrete.getId() != null) {
            result.put("id", concrete.getId());
        }
        if (concrete.getPowerState() != null) {
            result.put("state", concrete.getPowerState());
        }

        return result;
    }

    @Override
    public ResourceTypeHandler<VirtualMachineResource> getConcreteHandler() {
        return handler;
    }

    /**
     * Resolve an Azure VM size from abstract cpu and memory values.
     * Performs exact match first, then falls back to the closest larger instance.
     *
     * @param cpu    number of vCPUs requested
     * @param memory memory in GiB requested
     * @return the Azure VM size string (e.g., "Standard_B2s")
     */
    static String resolveVmSize(int cpu, int memory) {
        // Exact match
        var key = cpu + ":" + memory;
        var exact = VM_SIZE_TABLE.get(key);
        if (exact != null) {
            return exact;
        }

        // Closest larger instance: find the smallest spec where cpu >= requested AND memory >= requested
        for (var spec : SORTED_SPECS) {
            if (spec.cpu() >= cpu && spec.memory() >= memory) {
                return spec.size();
            }
        }

        // If nothing is large enough, return the biggest available
        return SORTED_SPECS[SORTED_SPECS.length - 1].size();
    }

    /**
     * Safely convert a numeric property value to int.
     * Handles Integer, Long, Double, and String representations.
     */
    private static int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
