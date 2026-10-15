package cloud.kitelang.provider.aws;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkClient;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The endpoint the AWS provider sends its calls to (kitecorp/kite#107): an {@code endpoint} in the
 * provider configuration, or else the AWS SDK's standard setting ({@code AWS_ENDPOINT_URL}), points
 * every client the provider builds at a local emulator such as Floci.
 *
 * <p>The standard setting is set here through {@code aws.endpointUrl}, the JVM system property the
 * SDK reads for {@code AWS_ENDPOINT_URL} (a test cannot set an environment variable of its own JVM).</p>
 */
class AwsProviderEndpointTest {

    private static final String SDK_ENDPOINT_PROPERTY = "aws.endpointUrl";
    /** configure() sets this one for the whole JVM; restored so other tests do not inherit it. */
    private static final String SDK_REGION_PROPERTY = "aws.region";

    private final Map<String, String> propertiesBefore = new HashMap<>();

    @BeforeEach
    void rememberSdkProperties() {
        for (var name : List.of(SDK_ENDPOINT_PROPERTY, SDK_REGION_PROPERTY)) {
            propertiesBefore.put(name, System.getProperty(name));
        }
        System.clearProperty(SDK_ENDPOINT_PROPERTY);
    }

    @AfterEach
    void restoreSdkProperties() {
        propertiesBefore.forEach((name, value) -> {
            if (value == null) {
                System.clearProperty(name);
            } else {
                System.setProperty(name, value);
            }
        });
    }

    @Test
    void everyClientUsesTheConfiguredEndpoint() {
        var provider = new AwsProvider();

        provider.configure(Map.of("region", "us-east-1", "endpoint", "http://127.0.0.1:4566"));

        assertEquals(everyClientAt("http://127.0.0.1:4566"), endpointsOf(provider));
    }

    @Test
    void theConfiguredEndpointWinsOverTheStandardSetting() {
        System.setProperty(SDK_ENDPOINT_PROPERTY, "http://127.0.0.1:4567");
        var provider = new AwsProvider();

        provider.configure(Map.of("region", "us-east-1", "endpoint", "http://127.0.0.1:4566"));

        assertEquals(everyClientAt("http://127.0.0.1:4566"), endpointsOf(provider));
    }

    @Test
    void withoutAConfiguredEndpointEveryClientUsesTheStandardSetting() {
        System.setProperty(SDK_ENDPOINT_PROPERTY, "http://127.0.0.1:4567");
        var provider = new AwsProvider();

        provider.configure(Map.of("region", "us-east-1"));

        assertEquals(everyClientAt("http://127.0.0.1:4567"), endpointsOf(provider));
    }

    @Test
    void anEndpointWithoutHttpOrHttpsIsRefusedWithTheSettingNamed() {
        var provider = new AwsProvider();

        var refused = assertThrows(IllegalArgumentException.class,
                () -> provider.configure(Map.of("region", "us-east-1", "endpoint", "localhost:4566")));

        assertEquals("The aws endpoint must be an http or https URL, such as http://127.0.0.1:4566,"
                + " but is: localhost:4566", refused.getMessage());
    }

    /**
     * The provider hands its clients to the resource types that implement {@link AwsClientAware};
     * a resource type that does not would build its own client, which knows nothing of the endpoint.
     */
    @Test
    void everyResourceTypeReceivesTheProvidersClients() {
        var provider = new AwsProvider();

        var notReceiving = provider.getResourceTypes().entrySet().stream()
                .filter(entry -> !(entry.getValue() instanceof AwsClientAware))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();

        assertEquals(List.of(), notReceiving);
    }

    private static Map<String, Optional<URI>> everyClientAt(String endpoint) {
        var uri = Optional.of(URI.create(endpoint));
        return Map.of("ec2", uri, "elasticloadbalancing", uri, "iam", uri, "route53", uri, "s3", uri);
    }

    /** The endpoint each of the provider's clients sends its calls to, by service name. */
    private static Map<String, Optional<URI>> endpointsOf(AwsProvider provider) {
        var endpoints = new TreeMap<String, Optional<URI>>();
        for (SdkClient client : List.of(provider.getEc2Client(), provider.getElbClient(), provider.getIamClient(),
                provider.getRoute53Client(), provider.getS3Client())) {
            endpoints.put(client.serviceName(), client.serviceClientConfiguration().endpointOverride());
        }
        return endpoints;
    }
}
