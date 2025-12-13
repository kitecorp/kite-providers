package cloud.kitelang.provider.aws;

import cloud.kitelang.provider.KiteProvider;
import cloud.kitelang.provider.ProviderServer;
import lombok.extern.log4j.Log4j2;

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
@Log4j2
public class AwsProvider extends KiteProvider {

    public AwsProvider() {
        super("aws", "0.1.0");
        log.info("AWS Provider initialized with resources: {}", getResourceTypes().keySet());
    }

    public static void main(String[] args) throws Exception {
        log.info("Starting AWS Provider...");
        ProviderServer.serve(new AwsProvider());
    }
}
