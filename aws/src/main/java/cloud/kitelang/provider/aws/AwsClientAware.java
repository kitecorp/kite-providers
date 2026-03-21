package cloud.kitelang.provider.aws;

import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Interface for resource type handlers that need AWS SDK clients.
 * Implemented by resource types so the provider can push configured clients
 * (with the correct profile and region) after {@code configure()} is called.
 *
 * <p>Each handler picks the client it needs from the arguments and ignores the rest.</p>
 */
public interface AwsClientAware {

    /**
     * Receive configured AWS clients from the provider.
     * Implementations should store only the client type they actually use.
     *
     * @param ec2Client     configured EC2 client
     * @param s3Client      configured S3 client
     * @param elbClient     configured ELBv2 client
     * @param route53Client configured Route53 client
     * @param iamClient     configured IAM client
     */
    void setAwsClients(Ec2Client ec2Client, S3Client s3Client,
                       ElasticLoadBalancingV2Client elbClient,
                       Route53Client route53Client, IamClient iamClient);
}
