package cloud.kitelang.provider.azure.networking;

import cloud.kitelang.provider.Diagnostic;
import cloud.kitelang.provider.ResourceTypeHandler;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.Region;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.resourcemanager.network.NetworkManager;
import com.azure.resourcemanager.network.models.NetworkSecurityGroup;
import com.azure.resourcemanager.network.models.SecurityRuleAccess;
import com.azure.resourcemanager.network.models.SecurityRuleDirection;
import com.azure.resourcemanager.network.models.SecurityRuleProtocol;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ResourceTypeHandler for Azure Network Security Group (NSG).
 * Implements CRUD operations for NSGs using Azure SDK.
 */
@Slf4j
public class NetworkSecurityGroupResourceType extends ResourceTypeHandler<NetworkSecurityGroupResource> {

    private final NetworkManager networkManager;

    public NetworkSecurityGroupResourceType() {
        var credential = new DefaultAzureCredentialBuilder().build();

        String subscriptionId = System.getenv("AZURE_SUBSCRIPTION_ID");
        if (subscriptionId == null || subscriptionId.isBlank()) {
            throw new IllegalStateException(
                    "AZURE_SUBSCRIPTION_ID environment variable must be set");
        }

        String tenantId = System.getenv("AZURE_TENANT_ID");
        var profile = new AzureProfile(tenantId, subscriptionId, AzureEnvironment.AZURE);

        this.networkManager = NetworkManager.authenticate(credential, profile);
    }

    public NetworkSecurityGroupResourceType(NetworkManager networkManager) {
        this.networkManager = networkManager;
    }

    @Override
    public NetworkSecurityGroupResource create(NetworkSecurityGroupResource resource) {
        log.info("Creating Network Security Group '{}' in resource group '{}' at '{}'",
                resource.getName(), resource.getResourceGroup(), resource.getLocation());

        var definition = networkManager.networkSecurityGroups()
                .define(resource.getName())
                .withRegion(Region.fromName(resource.getLocation()))
                .withExistingResourceGroup(resource.getResourceGroup());

        // Add security rules
        if (resource.getSecurityRules() != null && !resource.getSecurityRules().isEmpty()) {
            for (SecurityRule rule : resource.getSecurityRules()) {
                if ("Inbound".equalsIgnoreCase(rule.getDirection())) {
                    definition = addInboundRule(definition, rule);
                } else {
                    definition = addOutboundRule(definition, rule);
                }
            }
        }

        // Add tags
        if (resource.getTags() != null && !resource.getTags().isEmpty()) {
            definition = definition.withTags(resource.getTags());
        }

        NetworkSecurityGroup nsg = definition.create();
        log.info("Created Network Security Group: {}", nsg.id());

        return mapToResource(nsg);
    }

    private NetworkSecurityGroup.DefinitionStages.WithCreate addInboundRule(
            NetworkSecurityGroup.DefinitionStages.WithCreate definition,
            SecurityRule rule) {

        var ruleDefinition = definition.defineRule(rule.getName())
                .allowInbound();

        if ("Deny".equalsIgnoreCase(rule.getAccess())) {
            ruleDefinition = definition.defineRule(rule.getName()).denyInbound();
        }

        return ruleDefinition
                .fromAddress(rule.getSourceAddressPrefix())
                .fromPort(parsePort(rule.getSourcePortRange()))
                .toAddress(rule.getDestinationAddressPrefix())
                .toPort(parsePort(rule.getDestinationPortRange()))
                .withProtocol(mapProtocol(rule.getProtocol()))
                .withPriority(rule.getPriority())
                .withDescription(rule.getDescription())
                .attach();
    }

    private NetworkSecurityGroup.DefinitionStages.WithCreate addOutboundRule(
            NetworkSecurityGroup.DefinitionStages.WithCreate definition,
            SecurityRule rule) {

        var ruleDefinition = definition.defineRule(rule.getName())
                .allowOutbound();

        if ("Deny".equalsIgnoreCase(rule.getAccess())) {
            ruleDefinition = definition.defineRule(rule.getName()).denyOutbound();
        }

        return ruleDefinition
                .fromAddress(rule.getSourceAddressPrefix())
                .fromPort(parsePort(rule.getSourcePortRange()))
                .toAddress(rule.getDestinationAddressPrefix())
                .toPort(parsePort(rule.getDestinationPortRange()))
                .withProtocol(mapProtocol(rule.getProtocol()))
                .withPriority(rule.getPriority())
                .withDescription(rule.getDescription())
                .attach();
    }

    private int parsePort(String portRange) {
        if (portRange == null || "*".equals(portRange)) {
            return 0; // Any port
        }
        if (portRange.contains("-")) {
            // For ranges, return the start port (SDK handles ranges differently)
            return Integer.parseInt(portRange.split("-")[0]);
        }
        return Integer.parseInt(portRange);
    }

    private SecurityRuleProtocol mapProtocol(String protocol) {
        if (protocol == null || "*".equals(protocol)) {
            return SecurityRuleProtocol.ASTERISK;
        }
        return switch (protocol.toLowerCase()) {
            case "tcp" -> SecurityRuleProtocol.TCP;
            case "udp" -> SecurityRuleProtocol.UDP;
            case "icmp" -> SecurityRuleProtocol.ICMP;
            default -> SecurityRuleProtocol.ASTERISK;
        };
    }

    @Override
    public NetworkSecurityGroupResource read(NetworkSecurityGroupResource resource) {
        if (resource.getId() == null && (resource.getName() == null || resource.getResourceGroup() == null)) {
            log.warn("Cannot read NSG without id or (name and resourceGroup)");
            return null;
        }

        log.info("Reading Network Security Group '{}' in resource group '{}'",
                resource.getName(), resource.getResourceGroup());

        try {
            NetworkSecurityGroup nsg;
            if (resource.getId() != null) {
                nsg = networkManager.networkSecurityGroups().getById(resource.getId());
            } else {
                nsg = networkManager.networkSecurityGroups()
                        .getByResourceGroup(resource.getResourceGroup(), resource.getName());
            }

            if (nsg == null) {
                return null;
            }

            return mapToResource(nsg);

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("ResourceNotFound")) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public NetworkSecurityGroupResource update(NetworkSecurityGroupResource resource) {
        log.info("Updating Network Security Group '{}' in resource group '{}'",
                resource.getName(), resource.getResourceGroup());

        var current = read(resource);
        if (current == null) {
            throw new RuntimeException("Network Security Group not found: " + resource.getName());
        }

        NetworkSecurityGroup nsg = networkManager.networkSecurityGroups()
                .getByResourceGroup(resource.getResourceGroup(), resource.getName());

        var update = nsg.update();

        // Update security rules - remove old ones and add new ones
        if (resource.getSecurityRules() != null) {
            // Remove existing custom rules (not default rules which start with priority > 65000)
            for (var existingRule : nsg.securityRules().entrySet()) {
                if (existingRule.getValue().priority() < 65000) {
                    update = update.withoutRule(existingRule.getKey());
                }
            }

            // Add new rules
            for (SecurityRule rule : resource.getSecurityRules()) {
                if ("Inbound".equalsIgnoreCase(rule.getDirection())) {
                    update = addInboundRuleToUpdate(update, rule);
                } else {
                    update = addOutboundRuleToUpdate(update, rule);
                }
            }
        }

        // Update tags
        if (resource.getTags() != null) {
            update = update.withTags(resource.getTags());
        }

        nsg = update.apply();
        return mapToResource(nsg);
    }

    private NetworkSecurityGroup.Update addInboundRuleToUpdate(
            NetworkSecurityGroup.Update update,
            SecurityRule rule) {

        var ruleUpdate = update.defineRule(rule.getName())
                .allowInbound();

        if ("Deny".equalsIgnoreCase(rule.getAccess())) {
            ruleUpdate = update.defineRule(rule.getName()).denyInbound();
        }

        return ruleUpdate
                .fromAddress(rule.getSourceAddressPrefix())
                .fromPort(parsePort(rule.getSourcePortRange()))
                .toAddress(rule.getDestinationAddressPrefix())
                .toPort(parsePort(rule.getDestinationPortRange()))
                .withProtocol(mapProtocol(rule.getProtocol()))
                .withPriority(rule.getPriority())
                .withDescription(rule.getDescription())
                .attach();
    }

    private NetworkSecurityGroup.Update addOutboundRuleToUpdate(
            NetworkSecurityGroup.Update update,
            SecurityRule rule) {

        var ruleUpdate = update.defineRule(rule.getName())
                .allowOutbound();

        if ("Deny".equalsIgnoreCase(rule.getAccess())) {
            ruleUpdate = update.defineRule(rule.getName()).denyOutbound();
        }

        return ruleUpdate
                .fromAddress(rule.getSourceAddressPrefix())
                .fromPort(parsePort(rule.getSourcePortRange()))
                .toAddress(rule.getDestinationAddressPrefix())
                .toPort(parsePort(rule.getDestinationPortRange()))
                .withProtocol(mapProtocol(rule.getProtocol()))
                .withPriority(rule.getPriority())
                .withDescription(rule.getDescription())
                .attach();
    }

    @Override
    public boolean delete(NetworkSecurityGroupResource resource) {
        if (resource.getId() == null && (resource.getName() == null || resource.getResourceGroup() == null)) {
            log.warn("Cannot delete NSG without id or (name and resourceGroup)");
            return false;
        }

        log.info("Deleting Network Security Group '{}' in resource group '{}'",
                resource.getName(), resource.getResourceGroup());

        try {
            if (resource.getId() != null) {
                networkManager.networkSecurityGroups().deleteById(resource.getId());
            } else {
                networkManager.networkSecurityGroups()
                        .deleteByResourceGroup(resource.getResourceGroup(), resource.getName());
            }

            log.info("Deleted Network Security Group: {}", resource.getName());
            return true;

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("ResourceNotFound")) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public List<Diagnostic> validate(NetworkSecurityGroupResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getName() == null || resource.getName().isBlank()) {
            diagnostics.add(Diagnostic.error("name is required")
                    .withProperty("name"));
        } else {
            if (resource.getName().length() < 1 || resource.getName().length() > 80) {
                diagnostics.add(Diagnostic.error("name must be between 1 and 80 characters")
                        .withProperty("name"));
            }
        }

        if (resource.getResourceGroup() == null || resource.getResourceGroup().isBlank()) {
            diagnostics.add(Diagnostic.error("resourceGroup is required")
                    .withProperty("resourceGroup"));
        }

        if (resource.getLocation() == null || resource.getLocation().isBlank()) {
            diagnostics.add(Diagnostic.error("location is required")
                    .withProperty("location"));
        }

        // Validate security rules
        if (resource.getSecurityRules() != null) {
            for (int i = 0; i < resource.getSecurityRules().size(); i++) {
                var rule = resource.getSecurityRules().get(i);
                String prefix = "securityRules[" + i + "]";

                if (rule.getName() == null || rule.getName().isBlank()) {
                    diagnostics.add(Diagnostic.error("rule name is required")
                            .withProperty(prefix + ".name"));
                }

                if (rule.getPriority() == null || rule.getPriority() < 100 || rule.getPriority() > 4096) {
                    diagnostics.add(Diagnostic.error("priority must be between 100 and 4096")
                            .withProperty(prefix + ".priority"));
                }

                if (rule.getDirection() == null ||
                    (!rule.getDirection().equalsIgnoreCase("Inbound") &&
                     !rule.getDirection().equalsIgnoreCase("Outbound"))) {
                    diagnostics.add(Diagnostic.error("direction must be 'Inbound' or 'Outbound'")
                            .withProperty(prefix + ".direction"));
                }

                if (rule.getAccess() == null ||
                    (!rule.getAccess().equalsIgnoreCase("Allow") &&
                     !rule.getAccess().equalsIgnoreCase("Deny"))) {
                    diagnostics.add(Diagnostic.error("access must be 'Allow' or 'Deny'")
                            .withProperty(prefix + ".access"));
                }
            }
        }

        return diagnostics;
    }

    private NetworkSecurityGroupResource mapToResource(NetworkSecurityGroup nsg) {
        var resource = new NetworkSecurityGroupResource();

        // Input properties
        resource.setName(nsg.name());
        resource.setResourceGroup(nsg.resourceGroupName());
        resource.setLocation(nsg.regionName());

        // Map security rules
        if (nsg.securityRules() != null && !nsg.securityRules().isEmpty()) {
            var rules = new ArrayList<SecurityRule>();
            for (var entry : nsg.securityRules().entrySet()) {
                var azureRule = entry.getValue();
                var rule = SecurityRule.builder()
                        .name(azureRule.name())
                        .priority(azureRule.priority())
                        .direction(azureRule.direction().toString())
                        .access(azureRule.access().toString())
                        .protocol(azureRule.protocol().toString())
                        .sourceAddressPrefix(azureRule.sourceAddressPrefix())
                        .sourcePortRange(azureRule.sourcePortRange())
                        .destinationAddressPrefix(azureRule.destinationAddressPrefix())
                        .destinationPortRange(azureRule.destinationPortRange())
                        .description(azureRule.description())
                        .build();
                rules.add(rule);
            }
            resource.setSecurityRules(rules);
        }

        // Tags
        if (nsg.tags() != null && !nsg.tags().isEmpty()) {
            resource.setTags(new HashMap<>(nsg.tags()));
        }

        // Cloud-managed properties
        resource.setId(nsg.id());
        resource.setResourceGuid(nsg.innerModel().resourceGuid());

        var provisioningState = nsg.innerModel().provisioningState();
        if (provisioningState != null) {
            resource.setProvisioningState(provisioningState.toString());
        }

        // Network interfaces
        if (nsg.networkInterfaceIds() != null && !nsg.networkInterfaceIds().isEmpty()) {
            resource.setNetworkInterfaceIds(new ArrayList<>(nsg.networkInterfaceIds()));
        }

        return resource;
    }
}
