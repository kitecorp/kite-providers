package cloud.kitelang.provider.distribution;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Starts each provider distribution it is given and checks that it serves every resource
 * type, so a provider whose handlers fail to load cannot ship unnoticed.
 *
 * <p>The azure 0.2.0 release zip loaded none of its handlers (kitecorp/kite-providers#22).
 * The build that tagged the release was sound; the zip it published was a stale one, so
 * only a check of the distribution itself, started as a separate process, catches this.</p>
 */
class ProviderDistributionTest {

    /**
     * The resource types each provider serves, one per handler. A new handler or provider
     * fails this test until it is listed here.
     */
    private static final Map<String, Set<String>> EXPECTED_RESOURCE_TYPES = Map.of(
            "aws", Set.of(
                    "EbsVolume", "Ec2Instance", "ElasticIp", "HostedZone", "IamInstanceProfile", "IamPolicy",
                    "IamRole", "InternetGateway", "Listener", "LoadBalancer", "NatGateway", "RecordSet",
                    "RouteTable", "S3Bucket", "S3BucketPolicy", "SecurityGroup", "Subnet", "TargetGroup", "Vpc"),
            "azure", Set.of(
                    "BlobContainer", "DnsRecordSet", "DnsZone", "LoadBalancer", "ManagedDisk", "NatGateway",
                    "NetworkSecurityGroup", "PublicIpAddress", "ResourceGroup", "RouteTable", "StorageAccount",
                    "Subnet", "VirtualMachine", "Vnet"),
            "files", Set.of("File"),
            "gcp", Set.of(
                    "CloudStorageBucket", "ComputeInstance", "ForwardingRule", "ManagedZone", "ResourceRecordSet",
                    "Subnetwork", "VpcNetwork"),
            "grpc-example", Set.of("File"));

    @ParameterizedTest(name = "{0}")
    @MethodSource("distributions")
    void servesEveryResourceType(ProviderDistribution distribution) throws Exception {
        var expected = new TreeSet<>(expectedResourceTypes(distribution.name()));

        var run = ProviderRun.start(distribution);

        assertEquals(expected, run.resourceTypes(), () -> "Resource types served by " + distribution
                + " from " + distribution.directory() + "\nProvider log:\n" + String.join("\n", run.log()));
    }

    /** The distributions named by the build's {@code distributionDirs} property. */
    static List<ProviderDistribution> distributions() {
        var directories = System.getProperty("kite.distribution.dirs", "");
        if (directories.isBlank()) {
            fail("No distributions to check: run with -PdistributionDirs=<dir>[,<dir>...]");
        }
        return Arrays.stream(directories.split(","))
                .map(Path::of)
                .map(ProviderDistribution::read)
                .toList();
    }

    private static Set<String> expectedResourceTypes(String provider) {
        var expected = EXPECTED_RESOURCE_TYPES.get(provider);
        if (expected == null) {
            fail("No expected resource types for provider '" + provider + "': add them to EXPECTED_RESOURCE_TYPES");
        }
        return expected;
    }
}
