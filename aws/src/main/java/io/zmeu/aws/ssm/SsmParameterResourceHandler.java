package io.zmeu.aws.ssm;

import io.zmeu.api.ResourceHandler;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.ParameterType;
import software.amazon.awssdk.services.ssm.model.PutParameterRequest;

public class SsmParameterResourceHandler extends ResourceHandler<SsmParameter> {
    private SsmClient ssmClient = SsmClient.builder()
            .build();

    public SsmParameterResourceHandler() {
        super();
        var provider = DefaultCredentialsProvider.create();
        var creds = provider.resolveCredentials();
        System.out.println("Using credentials:");
        System.out.println("\tAccess key ID:     " + creds.accessKeyId());
        if (creds instanceof AwsSessionCredentials) {
            System.out.println("\tSession token set: yes");
        }
    }
    public SsmParameterResourceHandler(SsmClient ssmClient) {
        super();
        this.ssmClient = ssmClient;
    }


    @Override
    public SsmParameter create(SsmParameter resource) {
        var response = ssmClient.putParameter(PutParameterRequest.builder()
                .name(resource.getName())
                .value(resource.getValue())
                .type(ParameterType.SECURE_STRING)
                .overwrite(resource.getOverwrite())
                .build());

        return resource;
    }

    @Override
    public SsmParameter read(SsmParameter resource) {
        var request = GetParameterRequest.builder()
                .name(resource.getName())
                .withDecryption(true)
                .build();
        var response = ssmClient.getParameter(request);

        return SsmParameter.builder()
                .name(response.parameter().name())
                .value(response.parameter().value())
                .type(response.parameter().typeAsString())
                .arn(response.parameter().arn())
                .build();
    }

    @Override
    public SsmParameter update(SsmParameter resource) {
        var response = ssmClient.putParameter(PutParameterRequest.builder()
                .name(resource.getName())
                .value(resource.getValue())
                .type(ParameterType.fromValue(resource.getType()))
                .overwrite(true)
                .build());

        return resource;
    }

    @Override
    public boolean delete(SsmParameter resource) {
        return false;
    }
}
