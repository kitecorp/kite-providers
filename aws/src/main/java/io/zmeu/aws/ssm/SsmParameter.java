package io.zmeu.aws.ssm;

import io.zmeu.api.annotations.Property;
import io.zmeu.api.annotations.TypeName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
@TypeName("SSMParameter")
public class SsmParameter {
    @Property(immutable = true)
    private String name;
    @Property
    private String value;

    @Property
    private String description;

    @Property
    private String type;

    @Property
    private String keyId;

    @Property
    private Boolean overwrite;

    @Property
    private String allowedPattern;

//    @Property(name = "value", type = Type.String)
//    private List<Tag> tags;

    @Property
    private String tier;

    private String policies;

    private String dataType;
}
