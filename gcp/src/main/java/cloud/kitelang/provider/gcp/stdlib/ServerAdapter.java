package cloud.kitelang.provider.gcp.stdlib;

import cloud.kitelang.provider.ResourceTypeHandler;
import cloud.kitelang.provider.StandardTypeAdapter;
import cloud.kitelang.provider.gcp.compute.ComputeInstanceResource;
import cloud.kitelang.provider.gcp.compute.ComputeInstanceResourceType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adapts the standard library {@code Server} type to GCP {@code ComputeInstance}.
 *
 * <p>Property mapping:</p>
 * <ul>
 *   <li>{@code cpu} + {@code memory} -> {@code machineType} (via lookup table)</li>
 *   <li>{@code image} -> {@code imageFamily} (stored as-is)</li>
 *   <li>{@code name} -> {@code name}</li>
 *   <li>{@code region} -> {@code zone} (appends '-a' suffix as default zone)</li>
 *   <li>{@code tags} -> {@code labels}</li>
 * </ul>
 *
 * <p>Cloud-managed properties mapped back:</p>
 * <ul>
 *   <li>{@code publicIp} <- {@code externalIpAddress}</li>
 *   <li>{@code privateIp} <- {@code internalIpAddress}</li>
 *   <li>{@code id} <- {@code instanceId}</li>
 *   <li>{@code state} <- {@code status}</li>
 * </ul>
 */
public class ServerAdapter implements StandardTypeAdapter<ComputeInstanceResource> {

    /**
     * Lookup table mapping (cpu, memory) pairs to GCP machine types.
     * Key format: "cpu:memory" (both in integer form).
     */
    private static final LinkedHashMap<String, String> MACHINE_TYPE_TABLE = new LinkedHashMap<>();

    static {
        MACHINE_TYPE_TABLE.put("1:1", "e2-micro");
        MACHINE_TYPE_TABLE.put("1:2", "e2-small");
        MACHINE_TYPE_TABLE.put("2:4", "e2-medium");
        MACHINE_TYPE_TABLE.put("2:8", "e2-standard-2");
        MACHINE_TYPE_TABLE.put("4:16", "e2-standard-4");
        MACHINE_TYPE_TABLE.put("8:32", "e2-standard-8");
        MACHINE_TYPE_TABLE.put("16:64", "e2-standard-16");
    }

    /**
     * Sorted specs for closest-match lookup, ordered by total capacity.
     */
    private record MachineSpec(int cpu, int memory, String machineType) {}

    private static final MachineSpec[] SORTED_SPECS = {
            new MachineSpec(1, 1, "e2-micro"),
            new MachineSpec(1, 2, "e2-small"),
            new MachineSpec(2, 4, "e2-medium"),
            new MachineSpec(2, 8, "e2-standard-2"),
            new MachineSpec(4, 16, "e2-standard-4"),
            new MachineSpec(8, 32, "e2-standard-8"),
            new MachineSpec(16, 64, "e2-standard-16"),
    };

    private final ComputeInstanceResourceType handler;

    public ServerAdapter(ComputeInstanceResourceType handler) {
        this.handler = handler;
    }

    @Override
    public String standardTypeName() {
        return "Server";
    }

    @Override
    public String concreteTypeName() {
        return "ComputeInstance";
    }

    @Override
    @SuppressWarnings("unchecked")
    public ComputeInstanceResource toConcreteProperties(Map<String, Object> abstractProps) {
        var builder = ComputeInstanceResource.builder();

        // Map cpu + memory -> machineType
        var cpu = toInt(abstractProps.get("cpu"));
        var memory = toInt(abstractProps.get("memory"));
        builder.machineType(resolveMachineType(cpu, memory));

        // Map image -> imageFamily
        var image = (String) abstractProps.get("image");
        if (image != null) {
            builder.imageFamily(image);
            // Default to debian-cloud if not specified as project/family
            builder.imageProject("debian-cloud");
        }

        // Map name -> name
        var name = (String) abstractProps.get("name");
        if (name != null) {
            builder.name(name);
        }

        // Map region -> zone (append '-a' for default zone selection)
        var region = (String) abstractProps.get("region");
        if (region != null) {
            if (!region.matches(".*-[a-z]$")) {
                // Region specified without zone letter, append '-a'
                builder.zone(region + "-a");
            } else {
                builder.zone(region);
            }
        }

        // Map tags -> labels
        var abstractTags = abstractProps.get("tags");
        if (abstractTags instanceof Map<?, ?> rawTags) {
            var labels = new HashMap<String, String>();
            rawTags.forEach((k, v) -> labels.put(String.valueOf(k), String.valueOf(v)));
            builder.labels(labels);
        }

        return builder.build();
    }

    @Override
    public Map<String, Object> toAbstractProperties(ComputeInstanceResource concrete) {
        var result = new HashMap<String, Object>();

        if (concrete.getExternalIpAddress() != null) {
            result.put("publicIp", concrete.getExternalIpAddress());
        }
        if (concrete.getInternalIpAddress() != null) {
            result.put("privateIp", concrete.getInternalIpAddress());
        }
        if (concrete.getInstanceId() != null) {
            result.put("id", concrete.getInstanceId());
        }
        if (concrete.getStatus() != null) {
            result.put("state", concrete.getStatus());
        }

        return result;
    }

    @Override
    public ResourceTypeHandler<ComputeInstanceResource> getConcreteHandler() {
        return handler;
    }

    /**
     * Resolve a GCP machine type from abstract cpu and memory values.
     * Performs exact match first, then falls back to the closest larger instance.
     *
     * @param cpu    number of vCPUs requested
     * @param memory memory in GiB requested
     * @return the GCP machine type string (e.g., "e2-medium")
     */
    static String resolveMachineType(int cpu, int memory) {
        // Exact match
        var key = cpu + ":" + memory;
        var exact = MACHINE_TYPE_TABLE.get(key);
        if (exact != null) {
            return exact;
        }

        // Closest larger instance
        for (var spec : SORTED_SPECS) {
            if (spec.cpu() >= cpu && spec.memory() >= memory) {
                return spec.machineType();
            }
        }

        // If nothing is large enough, return the biggest available
        return SORTED_SPECS[SORTED_SPECS.length - 1].machineType();
    }

    /**
     * Safely convert a numeric property value to int.
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
