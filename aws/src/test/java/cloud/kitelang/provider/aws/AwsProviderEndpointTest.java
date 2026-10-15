package cloud.kitelang.provider.aws;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private String endpointPropertyBefore;

    @BeforeEach
    void rememberEndpointProperty() {
        endpointPropertyBefore = System.getProperty(SDK_ENDPOINT_PROPERTY);
        System.clearProperty(SDK_ENDPOINT_PROPERTY);
    }

    @AfterEach
    void restoreEndpointProperty() {
        if (endpointPropertyBefore == null) {
            System.clearProperty(SDK_ENDPOINT_PROPERTY);
        } else {
            System.setProperty(SDK_ENDPOINT_PROPERTY, endpointPropertyBefore);
        }
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
