package cloud.kitelang.provider.aws.stdlib;

import cloud.kitelang.provider.ResourceTypeHandler;
import cloud.kitelang.provider.StandardTypeAdapter;
import cloud.kitelang.provider.aws.compute.Ec2InstanceResource;
import cloud.kitelang.provider.aws.compute.Ec2InstanceResourceType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adapts the standard library {@code Server} type to AWS {@code Ec2Instance}.
 *
 * <p>Property mapping:</p>
 * <ul>
 *   <li>{@code cpu} + {@code memory} -> {@code instanceType} (via lookup table)</li>
 *   <li>{@code image} -> {@code ami} (stored as-is; AMI resolution is a future feature)</li>
 *   <li>{@code name} -> {@code tags.Name}</li>
 *   <li>{@code region} -> not mapped (handled at provider level)</li>
 *   <li>{@code tags} -> {@code tags}</li>
 * </ul>
 *
 * <p>Cloud-managed properties mapped back:</p>
 * <ul>
 *   <li>{@code publicIp} <- {@code publicIp}</li>
 *   <li>{@code privateIp} <- {@code privateIp}</li>
 *   <li>{@code id} <- {@code instanceId}</li>
 *   <li>{@code state} <- {@code state}</li>
 * </ul>
 */
public class ServerAdapter implements StandardTypeAdapter<Ec2InstanceResource> {

    /**
     * Lookup table mapping (cpu, memory) pairs to EC2 instance types.
     * Key format: "cpu:memory" (both in integer form).
     * Entries are ordered from smallest to largest for closest-match fallback.
     */
    private static final LinkedHashMap<String, String> INSTANCE_TYPE_TABLE = new LinkedHashMap<>();

    static {
        INSTANCE_TYPE_TABLE.put("1:1", "t3.micro");
        INSTANCE_TYPE_TABLE.put("1:2", "t3.small");
        INSTANCE_TYPE_TABLE.put("2:4", "t3.medium");
        INSTANCE_TYPE_TABLE.put("2:8", "t3.large");
        INSTANCE_TYPE_TABLE.put("4:16", "t3.xlarge");
        INSTANCE_TYPE_TABLE.put("8:32", "t3.2xlarge");
    }

    /**
     * Sorted entries for closest-match lookup, ordered by total capacity (cpu * memory).
     */
    private record InstanceSpec(int cpu, int memory, String instanceType) {}

    private static final InstanceSpec[] SORTED_SPECS = {
            new InstanceSpec(1, 1, "t3.micro"),
            new InstanceSpec(1, 2, "t3.small"),
            new InstanceSpec(2, 4, "t3.medium"),
            new InstanceSpec(2, 8, "t3.large"),
            new InstanceSpec(4, 16, "t3.xlarge"),
            new InstanceSpec(8, 32, "t3.2xlarge"),
    };

    private final Ec2InstanceResourceType handler;

    public ServerAdapter(Ec2InstanceResourceType handler) {
        this.handler = handler;
    }

    @Override
    public String standardTypeName() {
        return "Server";
    }

    @Override
    public String concreteTypeName() {
        return "Ec2Instance";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Ec2InstanceResource toConcreteProperties(Map<String, Object> abstractProps) {
        var builder = Ec2InstanceResource.builder();

        // Map cpu + memory -> instanceType
        var cpu = toInt(abstractProps.get("cpu"));
        var memory = toInt(abstractProps.get("memory"));
        builder.instanceType(resolveInstanceType(cpu, memory));

        // Map image -> ami
        // TODO: Implement abstract image name to AMI ID resolution.
        //       For now, the image value is stored as-is and must be a valid AMI ID.
        var image = (String) abstractProps.get("image");
        if (image != null) {
            builder.ami(image);
        }

        // Map name -> tags.Name, merging with any explicit tags
        var tags = new HashMap<String, String>();
        var abstractTags = abstractProps.get("tags");
        if (abstractTags instanceof Map<?, ?> rawTags) {
            rawTags.forEach((k, v) -> tags.put(String.valueOf(k), String.valueOf(v)));
        }
        var name = (String) abstractProps.get("name");
        if (name != null) {
            tags.put("Name", name);
        }
        if (!tags.isEmpty()) {
            builder.tags(tags);
        }

        return builder.build();
    }

    @Override
    public Map<String, Object> toAbstractProperties(Ec2InstanceResource concrete) {
        var result = new HashMap<String, Object>();

        if (concrete.getPublicIp() != null) {
            result.put("publicIp", concrete.getPublicIp());
        }
        if (concrete.getPrivateIp() != null) {
            result.put("privateIp", concrete.getPrivateIp());
        }
        if (concrete.getInstanceId() != null) {
            result.put("id", concrete.getInstanceId());
        }
        if (concrete.getState() != null) {
            result.put("state", concrete.getState());
        }

        return result;
    }

    @Override
    public ResourceTypeHandler<Ec2InstanceResource> getConcreteHandler() {
        return handler;
    }

    /**
     * Resolve an EC2 instance type from abstract cpu and memory values.
     * Performs exact match first, then falls back to the closest larger instance.
     *
     * @param cpu    number of vCPUs requested
     * @param memory memory in GiB requested
     * @return the EC2 instance type string (e.g., "t3.medium")
     */
    static String resolveInstanceType(int cpu, int memory) {
        // Exact match
        var key = cpu + ":" + memory;
        var exact = INSTANCE_TYPE_TABLE.get(key);
        if (exact != null) {
            return exact;
        }

        // Closest larger instance: find the smallest spec where cpu >= requested AND memory >= requested
        for (var spec : SORTED_SPECS) {
            if (spec.cpu() >= cpu && spec.memory() >= memory) {
                return spec.instanceType();
            }
        }

        // If nothing is large enough, return the biggest available
        return SORTED_SPECS[SORTED_SPECS.length - 1].instanceType();
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
