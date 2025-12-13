package cloud.kitelang.provider.aws;

import cloud.kitelang.provider.Diagnostic;
import cloud.kitelang.provider.ResourceTypeHandler;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.route53.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ResourceTypeHandler for AWS Route53 Record Set.
 * Implements CRUD operations using AWS Route53 SDK.
 */
@Log4j2
public class RecordSetResourceType extends ResourceTypeHandler<RecordSetResource> {

    private static final Set<String> VALID_TYPES = Set.of(
            "A", "AAAA", "CNAME", "MX", "TXT", "NS", "SOA", "SRV", "CAA", "PTR", "SPF"
    );
    private static final Set<String> VALID_FAILOVER = Set.of("PRIMARY", "SECONDARY");
    private static final Set<String> VALID_CONTINENTS = Set.of("AF", "AN", "AS", "EU", "OC", "NA", "SA");

    private final Route53Client route53Client;

    public RecordSetResourceType() {
        this.route53Client = Route53Client.create();
    }

    public RecordSetResourceType(Route53Client route53Client) {
        this.route53Client = route53Client;
    }

    @Override
    public RecordSetResource create(RecordSetResource resource) {
        log.info("Creating Record Set: {} ({})", resource.getName(), resource.getType());

        var recordSet = buildResourceRecordSet(resource);

        var changeBatch = ChangeBatch.builder()
                .changes(Change.builder()
                        .action(ChangeAction.CREATE)
                        .resourceRecordSet(recordSet)
                        .build())
                .build();

        var response = route53Client.changeResourceRecordSets(ChangeResourceRecordSetsRequest.builder()
                .hostedZoneId(resource.getHostedZoneId())
                .changeBatch(changeBatch)
                .build());

        log.info("Created Record Set: {} (change ID: {})", resource.getName(), response.changeInfo().id());

        // Wait for change to propagate (optional, could be async)
        waitForChange(response.changeInfo().id());

        return read(resource);
    }

    @Override
    public RecordSetResource read(RecordSetResource resource) {
        if (resource.getHostedZoneId() == null || resource.getName() == null || resource.getType() == null) {
            log.warn("Cannot read Record Set without hostedZoneId, name, and type");
            return null;
        }

        log.info("Reading Record Set: {} ({})", resource.getName(), resource.getType());

        try {
            var response = route53Client.listResourceRecordSets(ListResourceRecordSetsRequest.builder()
                    .hostedZoneId(resource.getHostedZoneId())
                    .startRecordName(resource.getName())
                    .startRecordType(RRType.fromValue(resource.getType()))
                    .maxItems("1")
                    .build());

            if (response.resourceRecordSets().isEmpty()) {
                return null;
            }

            var recordSet = response.resourceRecordSets().get(0);

            // Verify exact match
            if (!matchesRecord(recordSet, resource)) {
                return null;
            }

            return mapRecordSetToResource(recordSet, resource.getHostedZoneId());

        } catch (Exception e) {
            log.warn("Error reading record set: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public RecordSetResource update(RecordSetResource resource) {
        log.info("Updating Record Set: {} ({})", resource.getName(), resource.getType());

        var recordSet = buildResourceRecordSet(resource);

        var changeBatch = ChangeBatch.builder()
                .changes(Change.builder()
                        .action(ChangeAction.UPSERT)
                        .resourceRecordSet(recordSet)
                        .build())
                .build();

        var response = route53Client.changeResourceRecordSets(ChangeResourceRecordSetsRequest.builder()
                .hostedZoneId(resource.getHostedZoneId())
                .changeBatch(changeBatch)
                .build());

        log.info("Updated Record Set: {} (change ID: {})", resource.getName(), response.changeInfo().id());

        waitForChange(response.changeInfo().id());

        return read(resource);
    }

    @Override
    public boolean delete(RecordSetResource resource) {
        if (resource.getHostedZoneId() == null || resource.getName() == null || resource.getType() == null) {
            log.warn("Cannot delete Record Set without hostedZoneId, name, and type");
            return false;
        }

        log.info("Deleting Record Set: {} ({})", resource.getName(), resource.getType());

        try {
            // Read current record to get exact values needed for deletion
            var current = read(resource);
            if (current == null) {
                return false;
            }

            var recordSet = buildResourceRecordSet(current);

            var changeBatch = ChangeBatch.builder()
                    .changes(Change.builder()
                            .action(ChangeAction.DELETE)
                            .resourceRecordSet(recordSet)
                            .build())
                    .build();

            route53Client.changeResourceRecordSets(ChangeResourceRecordSetsRequest.builder()
                    .hostedZoneId(resource.getHostedZoneId())
                    .changeBatch(changeBatch)
                    .build());

            log.info("Deleted Record Set: {}", resource.getName());
            return true;

        } catch (InvalidChangeBatchException e) {
            if (e.getMessage() != null && e.getMessage().contains("not found")) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public List<Diagnostic> validate(RecordSetResource resource) {
        var diagnostics = new ArrayList<Diagnostic>();

        if (resource.getHostedZoneId() == null || resource.getHostedZoneId().isBlank()) {
            diagnostics.add(Diagnostic.error("hostedZoneId is required")
                    .withProperty("hostedZoneId"));
        }

        if (resource.getName() == null || resource.getName().isBlank()) {
            diagnostics.add(Diagnostic.error("name is required")
                    .withProperty("name"));
        }

        if (resource.getType() == null || resource.getType().isBlank()) {
            diagnostics.add(Diagnostic.error("type is required")
                    .withProperty("type"));
        } else if (!VALID_TYPES.contains(resource.getType().toUpperCase())) {
            diagnostics.add(Diagnostic.error("Invalid record type",
                    "Valid values: " + String.join(", ", VALID_TYPES))
                    .withProperty("type"));
        }

        // Either records/ttl or aliasTarget required
        if (resource.getAliasTarget() == null) {
            if (resource.getTtl() == null) {
                diagnostics.add(Diagnostic.error("ttl is required when not using alias")
                        .withProperty("ttl"));
            } else if (resource.getTtl() < 0) {
                diagnostics.add(Diagnostic.error("ttl must be non-negative")
                        .withProperty("ttl"));
            }

            if (resource.getRecords() == null || resource.getRecords().isEmpty()) {
                diagnostics.add(Diagnostic.error("records is required when not using alias")
                        .withProperty("records"));
            }
        } else {
            // Alias target validation
            if (resource.getAliasTarget().getHostedZoneId() == null) {
                diagnostics.add(Diagnostic.error("aliasTarget.hostedZoneId is required")
                        .withProperty("aliasTarget.hostedZoneId"));
            }
            if (resource.getAliasTarget().getDnsName() == null) {
                diagnostics.add(Diagnostic.error("aliasTarget.dnsName is required")
                        .withProperty("aliasTarget.dnsName"));
            }
        }

        // Failover validation
        if (resource.getFailover() != null && !VALID_FAILOVER.contains(resource.getFailover().toUpperCase())) {
            diagnostics.add(Diagnostic.error("Invalid failover value",
                    "Valid values: PRIMARY, SECONDARY")
                    .withProperty("failover"));
        }

        // Weight validation
        if (resource.getWeight() != null && (resource.getWeight() < 0 || resource.getWeight() > 255)) {
            diagnostics.add(Diagnostic.error("weight must be between 0 and 255")
                    .withProperty("weight"));
        }

        // SetIdentifier required for routing policies
        if ((resource.getWeight() != null || resource.getFailover() != null ||
             resource.getGeoLocation() != null || resource.getMultiValueAnswer() != null) &&
            (resource.getSetIdentifier() == null || resource.getSetIdentifier().isBlank())) {
            diagnostics.add(Diagnostic.error("setIdentifier is required for routing policies")
                    .withProperty("setIdentifier"));
        }

        // GeoLocation validation
        if (resource.getGeoLocation() != null) {
            var geo = resource.getGeoLocation();
            if (geo.getContinentCode() == null && geo.getCountryCode() == null) {
                diagnostics.add(Diagnostic.error("Either continentCode or countryCode is required for geolocation")
                        .withProperty("geoLocation"));
            }
            if (geo.getContinentCode() != null && !VALID_CONTINENTS.contains(geo.getContinentCode())) {
                diagnostics.add(Diagnostic.error("Invalid continent code",
                        "Valid values: " + String.join(", ", VALID_CONTINENTS))
                        .withProperty("geoLocation.continentCode"));
            }
        }

        return diagnostics;
    }

    private ResourceRecordSet buildResourceRecordSet(RecordSetResource resource) {
        var builder = ResourceRecordSet.builder()
                .name(resource.getName())
                .type(RRType.fromValue(resource.getType()));

        if (resource.getAliasTarget() != null) {
            // Alias record
            var alias = resource.getAliasTarget();
            builder.aliasTarget(software.amazon.awssdk.services.route53.model.AliasTarget.builder()
                    .hostedZoneId(alias.getHostedZoneId())
                    .dnsName(alias.getDnsName())
                    .evaluateTargetHealth(alias.getEvaluateTargetHealth() != null && alias.getEvaluateTargetHealth())
                    .build());
        } else {
            // Standard record
            builder.ttl(resource.getTtl());
            builder.resourceRecords(resource.getRecords().stream()
                    .map(r -> ResourceRecord.builder().value(r).build())
                    .collect(Collectors.toList()));
        }

        // Routing policies
        if (resource.getSetIdentifier() != null) {
            builder.setIdentifier(resource.getSetIdentifier());
        }

        if (resource.getWeight() != null) {
            builder.weight(resource.getWeight());
        }

        if (resource.getFailover() != null) {
            builder.failover(ResourceRecordSetFailover.fromValue(resource.getFailover()));
        }

        if (resource.getGeoLocation() != null) {
            var geo = resource.getGeoLocation();
            var geoBuilder = software.amazon.awssdk.services.route53.model.GeoLocation.builder();
            if (geo.getContinentCode() != null) {
                geoBuilder.continentCode(geo.getContinentCode());
            }
            if (geo.getCountryCode() != null) {
                geoBuilder.countryCode(geo.getCountryCode());
            }
            if (geo.getSubdivisionCode() != null) {
                geoBuilder.subdivisionCode(geo.getSubdivisionCode());
            }
            builder.geoLocation(geoBuilder.build());
        }

        if (resource.getHealthCheckId() != null) {
            builder.healthCheckId(resource.getHealthCheckId());
        }

        if (resource.getMultiValueAnswer() != null) {
            builder.multiValueAnswer(resource.getMultiValueAnswer());
        }

        return builder.build();
    }

    private boolean matchesRecord(ResourceRecordSet recordSet, RecordSetResource resource) {
        // Check name (Route53 always returns names with trailing dot)
        var expectedName = resource.getName().endsWith(".") ? resource.getName() : resource.getName() + ".";
        if (!recordSet.name().equals(expectedName)) {
            return false;
        }

        // Check type
        if (!recordSet.typeAsString().equals(resource.getType())) {
            return false;
        }

        // Check set identifier if using routing policy
        if (resource.getSetIdentifier() != null) {
            return resource.getSetIdentifier().equals(recordSet.setIdentifier());
        }

        return true;
    }

    private RecordSetResource mapRecordSetToResource(ResourceRecordSet recordSet, String hostedZoneId) {
        var resource = new RecordSetResource();

        resource.setHostedZoneId(hostedZoneId);
        resource.setName(recordSet.name());
        resource.setType(recordSet.typeAsString());
        resource.setTtl(recordSet.ttl());

        if (recordSet.resourceRecords() != null && !recordSet.resourceRecords().isEmpty()) {
            resource.setRecords(recordSet.resourceRecords().stream()
                    .map(ResourceRecord::value)
                    .collect(Collectors.toList()));
        }

        if (recordSet.aliasTarget() != null) {
            resource.setAliasTarget(RecordSetResource.AliasTarget.builder()
                    .hostedZoneId(recordSet.aliasTarget().hostedZoneId())
                    .dnsName(recordSet.aliasTarget().dnsName())
                    .evaluateTargetHealth(recordSet.aliasTarget().evaluateTargetHealth())
                    .build());
        }

        resource.setWeight(recordSet.weight());
        resource.setSetIdentifier(recordSet.setIdentifier());

        if (recordSet.failover() != null) {
            resource.setFailover(recordSet.failoverAsString());
        }

        if (recordSet.geoLocation() != null) {
            resource.setGeoLocation(RecordSetResource.GeoLocation.builder()
                    .continentCode(recordSet.geoLocation().continentCode())
                    .countryCode(recordSet.geoLocation().countryCode())
                    .subdivisionCode(recordSet.geoLocation().subdivisionCode())
                    .build());
        }

        resource.setHealthCheckId(recordSet.healthCheckId());
        resource.setMultiValueAnswer(recordSet.multiValueAnswer());

        return resource;
    }

    private void waitForChange(String changeId) {
        log.debug("Waiting for change {} to propagate", changeId);

        int maxAttempts = 60;
        int attempt = 0;

        while (attempt < maxAttempts) {
            var response = route53Client.getChange(GetChangeRequest.builder()
                    .id(changeId)
                    .build());

            if (response.changeInfo().status() == ChangeStatus.INSYNC) {
                log.debug("Change {} is now in sync", changeId);
                return;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for change", e);
            }
            attempt++;
        }

        log.warn("Timeout waiting for change {} to propagate", changeId);
    }
}
