package io.zmeu.aws.ssm;

import io.zmeu.api.Provider;
import org.pf4j.Extension;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.ParameterType;
import software.amazon.awssdk.services.ssm.model.PutParameterRequest;

@Extension
public class SsmParameterProvider extends Provider<SsmParameter> {
    private SsmClient ssmClient = SsmClient.builder()
            .build();

    @Override
    protected SsmParameter initResource() {
        return new SsmParameter();
    }

    @Override
    public SsmParameter create(SsmParameter resource) {
        var response = ssmClient.putParameter(PutParameterRequest.builder()
                .name(resource.getName())
                .value(resource.getValue())
                .type(ParameterType.SECURE_STRING)
                .overwrite(true)
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

                .build();
    }

    @Override
    public SsmParameter update(SsmParameter resource) {
        return null;
    }

    @Override
    public boolean delete(SsmParameter resource) {
        return false;
    }
}
