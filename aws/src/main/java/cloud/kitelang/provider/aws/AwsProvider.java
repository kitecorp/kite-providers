package cloud.kitelang.provider.aws;

import cloud.kitelang.provider.KiteProvider;
import cloud.kitelang.provider.ProviderServer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.Ec2ClientBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * AWS Provider for Kite.
 *
 * Provides resources for managing AWS infrastructure:
 * - Vpc: Virtual Private Cloud
 *
 * Example usage in Kite:
 * <pre>
 * provider "aws" {
 *     region = "us-east-1"
 * }
 *
 * resource Vpc main {
 *     cidr_block = "10.0.0.0/16"
 *     enable_dns_support = true
 *     enable_dns_hostnames = true
 *     tags = {
 *         Name = "main-vpc"
 *         Environment = "production"
 *     }
 * }
 * </pre>
 */
@Slf4j
public class AwsProvider extends KiteProvider {

    @Getter
    private volatile Ec2Client ec2Client;

    @Getter
    private volatile S3Client s3Client;

    private String profile;
    private String region;

    public AwsProvider() {
        // Name and version auto-loaded from provider-info.properties
        log.info("AWS Provider initialized with resources: {}", getResourceTypes().keySet());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void configure(Object configuration) {
        super.configure(configuration);

        if (configuration instanceof Map<?, ?> config) {
            // Extract configuration
            this.profile = (String) config.get("profile");
            this.region = (String) config.get("region");

            log.info("Configuring AWS provider with profile: {}, region: {}", profile, region);

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

            // Recreate clients with new configuration
            createClients();
        }
    }

    /**
     * Create AWS SDK clients with the configured credentials.
     */
    private void createClients() {
        // Build EC2 client
        Ec2ClientBuilder ec2Builder = Ec2Client.builder();
        S3ClientBuilder s3Builder = S3Client.builder();

        if (profile != null && !profile.isEmpty()) {
            var credentialsProvider = ProfileCredentialsProvider.create(profile);
            ec2Builder.credentialsProvider(credentialsProvider);
            s3Builder.credentialsProvider(credentialsProvider);
            log.debug("Using AWS profile: {}", profile);
        }

        if (region != null && !region.isEmpty()) {
            var awsRegion = Region.of(region);
            ec2Builder.region(awsRegion);
            s3Builder.region(awsRegion);
            log.debug("Using AWS region: {}", region);
        }

        this.ec2Client = ec2Builder.build();
        this.s3Client = s3Builder.build();
        log.info("AWS clients created successfully");
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
