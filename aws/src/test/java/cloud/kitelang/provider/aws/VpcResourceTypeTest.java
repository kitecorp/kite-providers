package cloud.kitelang.provider.aws;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for VpcResourceType with mocked AWS SDK.
 */
@ExtendWith(MockitoExtension.class)
class VpcResourceTypeTest {

    @Mock
    private Ec2Client ec2Client;

    private VpcResourceType resourceType;

    @BeforeEach
    void setUp() {
        resourceType = new VpcResourceType(ec2Client);
    }

    @Test
    void createVpc() {
        // Arrange
        var vpcResource = VpcResource.builder()
                .cidrBlock("10.0.0.0/16")
                .enableDnsSupport(true)
                .enableDnsHostnames(true)
                .tags(Map.of("Name", "test-vpc"))
                .build();

        var vpc = Vpc.builder()
                .vpcId("vpc-12345")
                .cidrBlock("10.0.0.0/16")
                .state(VpcState.AVAILABLE)
                .ownerId("123456789012")
                .instanceTenancy(Tenancy.DEFAULT)
                .tags(Tag.builder().key("Name").value("test-vpc").build())
                .build();

        when(ec2Client.createVpc(any(CreateVpcRequest.class)))
                .thenReturn(CreateVpcResponse.builder().vpc(vpc).build());

        when(ec2Client.describeVpcs(any(DescribeVpcsRequest.class)))
                .thenReturn(DescribeVpcsResponse.builder().vpcs(vpc).build());

        when(ec2Client.describeVpcAttribute(any(DescribeVpcAttributeRequest.class)))
                .thenAnswer(invocation -> {
                    DescribeVpcAttributeRequest req = invocation.getArgument(0);
                    if (req.attribute() == VpcAttributeName.ENABLE_DNS_SUPPORT) {
                        return DescribeVpcAttributeResponse.builder()
                                .enableDnsSupport(AttributeBooleanValue.builder().value(true).build())
                                .build();
                    } else {
                        return DescribeVpcAttributeResponse.builder()
                                .enableDnsHostnames(AttributeBooleanValue.builder().value(true).build())
                                .build();
                    }
                });

        when(ec2Client.describeRouteTables(any(DescribeRouteTablesRequest.class)))
                .thenReturn(DescribeRouteTablesResponse.builder()
                        .routeTables(RouteTable.builder().routeTableId("rtb-12345").build())
                        .build());

        when(ec2Client.describeNetworkAcls(any(DescribeNetworkAclsRequest.class)))
                .thenReturn(DescribeNetworkAclsResponse.builder()
                        .networkAcls(NetworkAcl.builder().networkAclId("acl-12345").build())
                        .build());

        when(ec2Client.describeSecurityGroups(any(DescribeSecurityGroupsRequest.class)))
                .thenReturn(DescribeSecurityGroupsResponse.builder()
                        .securityGroups(SecurityGroup.builder().groupId("sg-12345").build())
                        .build());

        // Act
        var result = resourceType.create(vpcResource);

        // Assert
        assertNotNull(result);
        assertEquals("vpc-12345", result.getVpcId());
        assertEquals("10.0.0.0/16", result.getCidrBlock());
        assertEquals("123456789012", result.getOwnerId());

        verify(ec2Client).createVpc(any(CreateVpcRequest.class));
        verify(ec2Client).modifyVpcAttribute(argThat(req ->
                req.enableDnsSupport() != null && req.enableDnsSupport().value()));
        verify(ec2Client).modifyVpcAttribute(argThat(req ->
                req.enableDnsHostnames() != null && req.enableDnsHostnames().value()));
        verify(ec2Client).createTags(any(CreateTagsRequest.class));
    }

    @Test
    void readVpc() {
        // Arrange
        var vpcResource = VpcResource.builder()
                .vpcId("vpc-12345")
                .build();

        var vpc = Vpc.builder()
                .vpcId("vpc-12345")
                .cidrBlock("10.0.0.0/16")
                .state(VpcState.AVAILABLE)
                .ownerId("123456789012")
                .instanceTenancy(Tenancy.DEFAULT)
                .build();

        when(ec2Client.describeVpcs(any(DescribeVpcsRequest.class)))
                .thenReturn(DescribeVpcsResponse.builder().vpcs(vpc).build());

        when(ec2Client.describeVpcAttribute(any(DescribeVpcAttributeRequest.class)))
                .thenReturn(DescribeVpcAttributeResponse.builder()
                        .enableDnsSupport(AttributeBooleanValue.builder().value(true).build())
                        .enableDnsHostnames(AttributeBooleanValue.builder().value(false).build())
                        .build());

        when(ec2Client.describeRouteTables(any(DescribeRouteTablesRequest.class)))
                .thenReturn(DescribeRouteTablesResponse.builder().routeTables(List.of()).build());

        when(ec2Client.describeNetworkAcls(any(DescribeNetworkAclsRequest.class)))
                .thenReturn(DescribeNetworkAclsResponse.builder().networkAcls(List.of()).build());

        when(ec2Client.describeSecurityGroups(any(DescribeSecurityGroupsRequest.class)))
                .thenReturn(DescribeSecurityGroupsResponse.builder().securityGroups(List.of()).build());

        // Act
        var result = resourceType.read(vpcResource);

        // Assert
        assertNotNull(result);
        assertEquals("vpc-12345", result.getVpcId());
        assertEquals("10.0.0.0/16", result.getCidrBlock());
        assertEquals("available", result.getState());
    }

    @Test
    void readVpcNotFound() {
        // Arrange
        var vpcResource = VpcResource.builder()
                .vpcId("vpc-nonexistent")
                .build();

        when(ec2Client.describeVpcs(any(DescribeVpcsRequest.class)))
                .thenThrow(Ec2Exception.builder()
                        .awsErrorDetails(AwsErrorDetails.builder()
                                .errorCode("InvalidVpcID.NotFound")
                                .build())
                        .build());

        // Act
        var result = resourceType.read(vpcResource);

        // Assert
        assertNull(result);
    }

    @Test
    void deleteVpc() {
        // Arrange
        var vpcResource = VpcResource.builder()
                .vpcId("vpc-12345")
                .build();

        // Act
        var result = resourceType.delete(vpcResource);

        // Assert
        assertTrue(result);
        verify(ec2Client).deleteVpc(argThat(req -> req.vpcId().equals("vpc-12345")));
    }

    @Test
    void deleteVpcNotFound() {
        // Arrange
        var vpcResource = VpcResource.builder()
                .vpcId("vpc-nonexistent")
                .build();

        when(ec2Client.deleteVpc(any(DeleteVpcRequest.class)))
                .thenThrow(Ec2Exception.builder()
                        .awsErrorDetails(AwsErrorDetails.builder()
                                .errorCode("InvalidVpcID.NotFound")
                                .build())
                        .build());

        // Act
        var result = resourceType.delete(vpcResource);

        // Assert
        assertFalse(result);
    }

    @Test
    void validateMissingCidrBlock() {
        // Arrange
        var vpcResource = new VpcResource();

        // Act
        var diagnostics = resourceType.validate(vpcResource);

        // Assert
        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.get(0).getSummary().contains("cidr_block"));
    }

    @Test
    void validateInvalidCidrBlock() {
        // Arrange
        var vpcResource = VpcResource.builder()
                .cidrBlock("invalid-cidr")
                .build();

        // Act
        var diagnostics = resourceType.validate(vpcResource);

        // Assert
        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.get(0).getSummary().contains("Invalid CIDR"));
    }

    @Test
    void validateInvalidTenancy() {
        // Arrange
        var vpcResource = VpcResource.builder()
                .cidrBlock("10.0.0.0/16")
                .instanceTenancy("invalid")
                .build();

        // Act
        var diagnostics = resourceType.validate(vpcResource);

        // Assert
        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.get(0).getSummary().contains("instance_tenancy"));
    }

    @Test
    void validateValidResource() {
        // Arrange
        var vpcResource = VpcResource.builder()
                .cidrBlock("10.0.0.0/16")
                .instanceTenancy("default")
                .build();

        // Act
        var diagnostics = resourceType.validate(vpcResource);

        // Assert
        assertTrue(diagnostics.isEmpty());
    }
}
