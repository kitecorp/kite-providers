package cloud.kitelang.provider.aws;

import cloud.kitelang.provider.KiteProvider;
import cloud.kitelang.provider.ProviderServer;
import cloud.kitelang.provider.aws.compute.Ec2InstanceResourceType;
import cloud.kitelang.provider.aws.dns.HostedZoneResourceType;
import cloud.kitelang.provider.aws.dns.RecordSetResourceType;
import cloud.kitelang.provider.aws.loadbalancing.LoadBalancerResourceType;
import cloud.kitelang.provider.aws.networking.SubnetResourceType;
import cloud.kitelang.provider.aws.networking.VpcResourceType;
import cloud.kitelang.provider.aws.stdlib.*;
import cloud.kitelang.provider.aws.storage.S3BucketResourceType;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * AWS Provider for Kite: networking, compute, storage, IAM, DNS and load balancing resources.
 *
 * <p>It is configured by the {@code aws} credential of the environment in {@code kitefile.yml}:</p>
 * <pre>
 * environments:
 *   - name: dev
 *     credentials:
 *       - type: aws
 *         profile: dev-sandbox               # optional, a profile of the shared AWS config files
 *         region: us-east-1
 *         properties:
 *           endpoint: http://127.0.0.1:4566  # optional, e.g. a local emulator such as Floci
 * </pre>
 *
 * <p>Every client the provider builds sends its calls to {@code endpoint}. Without one, the AWS
 * SDK's own endpoint settings apply, such as the {@code AWS_ENDPOINT_URL} environment variable.</p>
 */
@Slf4j
public class AwsProvider extends KiteProvider {

    @Getter
    private volatile Ec2Client ec2Client;

    @Getter
    private volatile S3Client s3Client;

    @Getter
    private volatile ElasticLoadBalancingV2Client elbClient;

    @Getter
    private volatile Route53Client route53Client;

    @Getter
    private volatile IamClient iamClient;

    private String profile;
    private String region;
    private String endpoint;

    public AwsProvider() {
        // Name and version auto-loaded from provider-info.properties
        // Resource types are discovered by the superclass constructor
        registerStandardTypeAdapters();
        log.info("AWS Provider initialized with resources: {}", getResourceTypes().keySet());
    }

    /**
     * Register standard library type adapters for all supported mappings.
     * Each adapter bridges a provider-agnostic stdlib type to its AWS-specific implementation.
     * Must be called after resource types are discovered (handled by superclass constructor).
     */
    @SuppressWarnings("unchecked")
    private void registerStandardTypeAdapters() {
        for (var type : getResourceTypes().values()) {
            switch (type) {
                case Ec2InstanceResourceType typed -> registerAdapter(new ServerAdapter(typed));
                case VpcResourceType type1 -> registerAdapter(new NetworkAdapter(type1));
                case SubnetResourceType type2 -> registerAdapter(new SubnetAdapter(type2));
                case LoadBalancerResourceType type3 -> registerAdapter(new LoadBalancerAdapter(type3));
                case S3BucketResourceType type4 -> registerAdapter(new BucketAdapter(type4));
                case HostedZoneResourceType type5 -> registerAdapter(new DnsZoneAdapter(type5));
                case RecordSetResourceType type6 -> registerAdapter(new DnsRecordAdapter(type6));
                default -> {
                }
            }
        }

        log.info("Registered {} standard type adapters", getStandardTypeAdapters().size());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void configure(Object configuration) {
        super.configure(configuration);

        if (configuration instanceof Map<?, ?> config) {
            // Extract configuration
            this.profile = (String) config.get("profile");
            this.region = (String) config.get("region");
            this.endpoint = (String) config.get("endpoint");

            log.info("Configuring AWS provider with profile: {}, region: {}, endpoint: {}", profile, region, endpoint);

            // Set environment/system properties so AWS SDK picks them up
            // This affects all subsequent client creations in this JVM
            if (profile != null && !profile.isEmpty()) {
                System.setProperty("aws.profile", profile);
                log.info("Set aws.profile system property to: {}", profile);
            }
            if (region != null && !region.isEmpty()) {
                System.setProperty("aws.region", region);
                log.info("Set aws.region system property to: {}", region);
            }

            // Recreate clients with new configuration and push to resource types
            createClients();
            wireClientsToResourceTypes();
        }
    }

    /**
     * Create all AWS SDK clients with the configured credentials.
     * Builds EC2, S3, ELBv2, Route53, and IAM clients using the profile, region
     * and endpoint from the kitefile configuration.
     */
    private void createClients() {
        AwsCredentialsProvider credentialsProvider = null;
        Region awsRegion = null;

        if (profile != null && !profile.isEmpty()) {
            credentialsProvider = ProfileCredentialsProvider.create(profile);
            log.debug("Using AWS profile: {}", profile);
        }
        if (region != null && !region.isEmpty()) {
            awsRegion = Region.of(region);
            log.debug("Using AWS region: {}", region);
        }

        this.ec2Client = configureBuilder(Ec2Client.builder(), credentialsProvider, awsRegion).build();
        this.s3Client = configureBuilder(S3Client.builder(), credentialsProvider, awsRegion).build();
        this.elbClient = configureBuilder(ElasticLoadBalancingV2Client.builder(), credentialsProvider, awsRegion).build();
        // Route53 and IAM are global services, but credentials still apply
        this.route53Client = configureBuilder(Route53Client.builder(), credentialsProvider, null).build();
        this.iamClient = configureBuilder(IamClient.builder(), credentialsProvider, null).build();

        log.info("AWS clients created successfully");
    }

    /**
     * Apply credentials, region and the configured endpoint to any AWS client builder.
     *
     * @param builder             the AWS client builder
     * @param credentialsProvider the credentials provider, or null for default chain
     * @param awsRegion           the region, or null to use the SDK default
     * @return the configured builder (same instance, for chaining)
     */
    @SuppressWarnings("unchecked")
    private <B extends AwsClientBuilder<B, ?>> B configureBuilder(
            B builder, AwsCredentialsProvider credentialsProvider, Region awsRegion) {
        if (credentialsProvider != null) {
            builder.credentialsProvider(credentialsProvider);
        }
        if (awsRegion != null) {
            builder.region(awsRegion);
        }
        // Without an override the SDK resolves the endpoint itself, which is what keeps
        // AWS_ENDPOINT_URL and AWS_ENDPOINT_URL_<SERVICE> working; an override replaces both
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint.trim()));
        }
        return builder;
    }

    /**
     * Push the configured clients to all registered resource type handlers.
     * Each handler that implements {@link AwsClientAware} receives the appropriate client
     * so it does not fall back to creating its own unconfigured client.
     */
    private void wireClientsToResourceTypes() {
        for (var handler : getResourceTypes().values()) {
            if (handler instanceof AwsClientAware aware) {
                aware.setAwsClients(ec2Client, s3Client, elbClient, route53Client, iamClient);
            }
        }
        log.debug("Wired AWS clients to {} resource type handlers", getResourceTypes().size());
    }

    /**
     * Get or create an EC2 client.
     * Creates a default client if not yet configured.
     */
    public Ec2Client getOrCreateEc2Client() {
        if (ec2Client == null) {
            synchronized (this) {
                if (ec2Client == null) {
                    ec2Client = Ec2Client.create();
                }
            }
        }
        return ec2Client;
    }

    /**
     * Get or create an S3 client.
     * Creates a default client if not yet configured.
     */
    public S3Client getOrCreateS3Client() {
        if (s3Client == null) {
            synchronized (this) {
                if (s3Client == null) {
                    s3Client = S3Client.create();
                }
            }
        }
        return s3Client;
    }

    public static void main(String[] args) throws Exception {
        // Configure logging to use a simpler format for gRPC plugin output
        configureLogging();

        log.info("Starting AWS Provider...");
        ProviderServer.serve(new AwsProvider());
    }

    /**
     * Configure JUL logging to minimize output.
     * The engine captures stderr and logs it, so we want to reduce noise.
     * Only warnings and errors are shown to avoid redundant logging.
     */
    private static void configureLogging() {
        // Set system property for simpler log format: LEVEL: message
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%4$s: %5$s%6$s%n");

        // Configure root logger to WARNING level to reduce noise
        // The engine already logs operations, so provider INFO logs are redundant
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.WARNING);

        // Remove existing handlers and add a new one with the simple format
        for (var handler : rootLogger.getHandlers()) {
            rootLogger.removeHandler(handler);
        }

        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.WARNING);
        handler.setFormatter(new SimpleFormatter());
        rootLogger.addHandler(handler);
    }
}
