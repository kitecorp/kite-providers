package io.zmeu.file;

import io.zmeu.api.Provider;
import io.zmeu.api.Resources;
import org.pf4j.Extension;

import java.util.List;

@Extension
public class FileProvider implements Provider {

    @Override
    public Resources getResources() {
        var resource = new FileResource();

        return Resources.builder()
                .resources(List.of(resource))
                .build();
    }

}