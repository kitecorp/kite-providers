package cloud.kitelang.provider.azure;

import cloud.kitelang.provider.azure.networking.VnetResource;
import cloud.kitelang.provider.azure.networking.VnetResourceType;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.network.NetworkManager;
import com.azure.resourcemanager.network.fluent.models.VirtualNetworkInner;
import com.azure.resourcemanager.network.models.Network;
import com.azure.resourcemanager.network.models.Networks;
import com.azure.resourcemanager.network.models.ProvisioningState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for VnetResourceType with mocked Azure SDK.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class VnetResourceTypeTest {

    @Mock
    private NetworkManager networkManager;

    @Mock
    private Networks networks;

    @Mock
    private Network network;

    @Mock
    private Network.DefinitionStages.Blank definitionBlank;

    @Mock
    private Network.DefinitionStages.WithGroup definitionWithGroup;

    @Mock
    private Network.DefinitionStages.WithCreate definitionWithCreate;

    @Mock
    private Network.DefinitionStages.WithCreateAndSubnet definitionWithCreateAndSubnet;

    @Mock
    private VirtualNetworkInner innerModel;

    private VnetResourceType resourceType;

    @BeforeEach
    void setUp() {
        lenient().when(networkManager.networks()).thenReturn(networks);
        resourceType = new VnetResourceType(networkManager);
    }

    @Test
    void createVnet() {
        // Arrange
        var vnetResource = VnetResource.builder()
                .name("test-vnet")
                .resourceGroup("test-rg")
                .location("eastus")
                .addressSpaces(List.of("10.0.0.0/16"))
                .tags(Map.of("Environment", "test"))
                .build();

        setupDefinitionMocks();
        when(definitionWithCreate.create()).thenReturn(network);
        setupNetworkMocks("test-vnet", "test-rg", "eastus", List.of("10.0.0.0/16"));

        // Act
        var result = resourceType.create(vnetResource);

        // Assert
        assertNotNull(result);
        assertEquals("test-vnet", result.getName());
        assertEquals("test-rg", result.getResourceGroup());
        assertEquals("eastus", result.getLocation());
        assertEquals(List.of("10.0.0.0/16"), result.getAddressSpaces());
        assertEquals("/subscriptions/sub/resourceGroups/test-rg/providers/Microsoft.Network/virtualNetworks/test-vnet", result.getId());

        verify(networks).define("test-vnet");
        verify(definitionWithCreateAndSubnet).create();
    }

    @Test
    void createVnetWithMultipleAddressSpaces() {
        // Arrange
        var vnetResource = VnetResource.builder()
                .name("multi-vnet")
                .resourceGroup("test-rg")
                .location("westus")
                .addressSpaces(List.of("10.0.0.0/16", "172.16.0.0/16"))
                .build();

        setupDefinitionMocks();
        when(definitionWithCreate.withAddressSpace("172.16.0.0/16")).thenReturn(definitionWithCreateAndSubnet);
        when(definitionWithCreateAndSubnet.create()).thenReturn(network);
        when(definitionWithCreate.create()).thenReturn(network);
        setupNetworkMocks("multi-vnet", "test-rg", "westus", List.of("10.0.0.0/16", "172.16.0.0/16"));

        // Act
        var result = resourceType.create(vnetResource);

        // Assert
        assertNotNull(result);
        assertEquals(List.of("10.0.0.0/16", "172.16.0.0/16"), result.getAddressSpaces());
    }

    @Test
    void readVnet() {
        // Arrange
        var vnetResource = VnetResource.builder()
                .name("test-vnet")
                .resourceGroup("test-rg")
                .build();

        when(networks.getByResourceGroup("test-rg", "test-vnet")).thenReturn(network);
        setupNetworkMocks("test-vnet", "test-rg", "eastus", List.of("10.0.0.0/16"));

        // Act
        var result = resourceType.read(vnetResource);

        // Assert
        assertNotNull(result);
        assertEquals("test-vnet", result.getName());
        assertEquals("test-rg", result.getResourceGroup());
        assertEquals("eastus", result.getLocation());
    }

    @Test
    void readVnetById() {
        // Arrange
        var vnetResource = VnetResource.builder()
                .id("/subscriptions/sub/resourceGroups/test-rg/providers/Microsoft.Network/virtualNetworks/test-vnet")
                .build();

        when(networks.getById("/subscriptions/sub/resourceGroups/test-rg/providers/Microsoft.Network/virtualNetworks/test-vnet"))
                .thenReturn(network);
        setupNetworkMocks("test-vnet", "test-rg", "eastus", List.of("10.0.0.0/16"));

        // Act
        var result = resourceType.read(vnetResource);

        // Assert
        assertNotNull(result);
        assertEquals("test-vnet", result.getName());
    }

    @Test
    void readVnetNotFound() {
        // Arrange
        var vnetResource = VnetResource.builder()
                .name("nonexistent-vnet")
                .resourceGroup("test-rg")
                .build();

        when(networks.getByResourceGroup("test-rg", "nonexistent-vnet"))
                .thenThrow(new ManagementException("ResourceNotFound", null));

        // Act
        var result = resourceType.read(vnetResource);

        // Assert
        assertNull(result);
    }

    @Test
    void deleteVnet() {
        // Arrange
        var vnetResource = VnetResource.builder()
                .name("test-vnet")
                .resourceGroup("test-rg")
                .build();

        doNothing().when(networks).deleteByResourceGroup("test-rg", "test-vnet");

        // Act
        var result = resourceType.delete(vnetResource);

        // Assert
        assertTrue(result);
        verify(networks).deleteByResourceGroup("test-rg", "test-vnet");
    }

    @Test
    void deleteVnetById() {
        // Arrange
        var vnetResource = VnetResource.builder()
                .id("/subscriptions/sub/resourceGroups/test-rg/providers/Microsoft.Network/virtualNetworks/test-vnet")
                .build();

        doNothing().when(networks).deleteById("/subscriptions/sub/resourceGroups/test-rg/providers/Microsoft.Network/virtualNetworks/test-vnet");

        // Act
        var result = resourceType.delete(vnetResource);

        // Assert
        assertTrue(result);
        verify(networks).deleteById("/subscriptions/sub/resourceGroups/test-rg/providers/Microsoft.Network/virtualNetworks/test-vnet");
    }

    @Test
    void deleteVnetNotFound() {
        // Arrange
        var vnetResource = VnetResource.builder()
                .name("nonexistent-vnet")
                .resourceGroup("test-rg")
                .build();

        doThrow(new ManagementException("ResourceNotFound", null))
                .when(networks).deleteByResourceGroup("test-rg", "nonexistent-vnet");

        // Act
        var result = resourceType.delete(vnetResource);

        // Assert
        assertFalse(result);
    }

    @Test
    void validateMissingName() {
        // Arrange
        var vnetResource = VnetResource.builder()
                .resourceGroup("test-rg")
                .location("eastus")
                .addressSpaces(List.of("10.0.0.0/16"))
                .build();

        // Act
        var diagnostics = resourceType.validate(vnetResource);

        // Assert
        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.get(0).getSummary().contains("name"));
    }

    @Test
    void validateMissingResourceGroup() {
        // Arrange
        var vnetResource = VnetResource.builder()
                .name("test-vnet")
                .location("eastus")
                .addressSpaces(List.of("10.0.0.0/16"))
                .build();

        // Act
        var diagnostics = resourceType.validate(vnetResource);

        // Assert
        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.get(0).getSummary().contains("resourceGroup"));
    }

    @Test
    void validateMissingLocation() {
        // Arrange
        var vnetResource = VnetResource.builder()
                .name("test-vnet")
                .resourceGroup("test-rg")
                .addressSpaces(List.of("10.0.0.0/16"))
                .build();

        // Act
        var diagnostics = resourceType.validate(vnetResource);

        // Assert
        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.get(0).getSummary().contains("location"));
    }

    @Test
    void validateMissingAddressSpaces() {
        // Arrange
        var vnetResource = VnetResource.builder()
                .name("test-vnet")
                .resourceGroup("test-rg")
                .location("eastus")
                .build();

        // Act
        var diagnostics = resourceType.validate(vnetResource);

        // Assert
        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.get(0).getSummary().contains("addressSpace"));
    }

    @Test
    void validateInvalidCidrFormat() {
        // Arrange
        var vnetResource = VnetResource.builder()
                .name("test-vnet")
                .resourceGroup("test-rg")
                .location("eastus")
                .addressSpaces(List.of("invalid-cidr"))
                .build();

        // Act
        var diagnostics = resourceType.validate(vnetResource);

        // Assert
        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.get(0).getSummary().contains("CIDR"));
    }

    @Test
    void validateInvalidNameLength() {
        // Arrange
        var vnetResource = VnetResource.builder()
                .name("x") // Too short
                .resourceGroup("test-rg")
                .location("eastus")
                .addressSpaces(List.of("10.0.0.0/16"))
                .build();

        // Act
        var diagnostics = resourceType.validate(vnetResource);

        // Assert
        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.get(0).getSummary().contains("2 and 64"));
    }

    @Test
    void validateValidResource() {
        // Arrange
        var vnetResource = VnetResource.builder()
                .name("test-vnet")
                .resourceGroup("test-rg")
                .location("eastus")
                .addressSpaces(List.of("10.0.0.0/16"))
                .build();

        // Act
        var diagnostics = resourceType.validate(vnetResource);

        // Assert
        assertTrue(diagnostics.isEmpty());
    }

    private void setupDefinitionMocks() {
        when(networks.define(anyString())).thenReturn(definitionBlank);
        when(definitionBlank.withRegion(any(com.azure.core.management.Region.class))).thenReturn(definitionWithGroup);
        when(definitionWithGroup.withExistingResourceGroup(anyString())).thenReturn(definitionWithCreate);
        when(definitionWithCreate.withAddressSpace(anyString())).thenReturn(definitionWithCreateAndSubnet);
        when(definitionWithCreateAndSubnet.withAddressSpace(anyString())).thenReturn(definitionWithCreateAndSubnet);
        when(definitionWithCreateAndSubnet.withDnsServer(anyString())).thenReturn(definitionWithCreateAndSubnet);
        when(definitionWithCreateAndSubnet.withTags(any())).thenReturn(definitionWithCreateAndSubnet);
        when(definitionWithCreateAndSubnet.create()).thenReturn(network);
        when(definitionWithCreate.withTags(any())).thenReturn(definitionWithCreate);
    }

    private void setupNetworkMocks(String name, String resourceGroup, String location, List<String> addressSpaces) {
        when(network.name()).thenReturn(name);
        when(network.resourceGroupName()).thenReturn(resourceGroup);
        when(network.regionName()).thenReturn(location);
        when(network.addressSpaces()).thenReturn(addressSpaces);
        when(network.dnsServerIPs()).thenReturn(List.of());
        when(network.isDdosProtectionEnabled()).thenReturn(false);
        when(network.isVmProtectionEnabled()).thenReturn(false);
        when(network.tags()).thenReturn(Map.of());
        when(network.id()).thenReturn("/subscriptions/sub/resourceGroups/" + resourceGroup + "/providers/Microsoft.Network/virtualNetworks/" + name);
        when(network.subnets()).thenReturn(Map.of());
        when(network.innerModel()).thenReturn(innerModel);
        when(innerModel.resourceGuid()).thenReturn("guid-12345");
        when(innerModel.provisioningState()).thenReturn(ProvisioningState.SUCCEEDED);
    }
}
