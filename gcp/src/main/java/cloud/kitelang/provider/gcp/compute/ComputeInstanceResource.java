package cloud.kitelang.provider.gcp.compute;

import cloud.kitelang.api.annotations.Cloud;
import cloud.kitelang.api.annotations.Property;
import cloud.kitelang.api.annotations.TypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * GCP Compute Engine Instance - a virtual machine in the cloud.
 *
 * Example usage:
 * <pre>
 * resource ComputeInstance web {
 *     name = "web-server"
 *     machineType = "e2-medium"
 *     zone = "us-central1-a"
 *     imageFamily = "debian-12"
 *     imageProject = "debian-cloud"
 *     networkInterface = "default"
 *     tags = ["http-server", "https-server"]
 *     labels = {
 *         environment: "production"
 *     }
 * }
 * </pre>
 *
 * @see <a href="https://cloud.google.com/compute/docs/instances">GCP Compute Engine Documentation</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("ComputeInstance")
public class ComputeInstanceResource {

    @Property(description = "The name of the instance", optional = false)
    private String name;

    @Property(description = "The machine type (e.g., e2-medium, n1-standard-1)", optional = false)
    private String machineType;

    @Property(description = "The zone where the instance will be created (e.g., us-central1-a)", optional = false)
    private String zone;

    @Property(description = "The image family to use for the boot disk (e.g., debian-12)")
    private String imageFamily;

    @Property(description = "The project containing the image family (e.g., debian-cloud)")
    private String imageProject;

    @Property(description = "The VPC network name or self-link for the primary network interface")
    private String networkInterface;

    @Property(description = "The subnetwork name or self-link for the primary network interface")
    private String subnetwork;

    @Property(description = "Network tags for firewall rules")
    private List<String> tags;

    @Property(description = "Labels to apply to the instance")
    private Map<String, String> labels;

    @Property(description = "Boot disk size in GB")
    private Integer diskSizeGb;

    @Property(description = "Boot disk type",
              validValues = {"pd-standard", "pd-ssd", "pd-balanced"})
    private String diskType;

    @Property(description = "Whether to assign an external IP address")
    private Boolean externalIp = true;

    @Property(description = "Startup script content")
    private String startupScript;

    @Property(description = "The service account email for the instance")
    private String serviceAccount;

    @Property(description = "OAuth2 scopes for the service account")
    private List<String> scopes;

    @Property(description = "Whether to enable preemptible/spot scheduling")
    private Boolean preemptible = false;

    // --- Cloud-managed properties (read-only) ---

    @Cloud(importable = true)
    @Property(description = "The unique instance ID assigned by GCP")
    private String instanceId;

    @Cloud
    @Property(description = "The instance status (PROVISIONING, STAGING, RUNNING, STOPPING, STOPPED, TERMINATED)")
    private String status;

    @Cloud
    @Property(description = "The self-link URL of the instance")
    private String selfLink;

    @Cloud
    @Property(description = "The external (public) IP address")
    private String externalIpAddress;

    @Cloud
    @Property(description = "The internal (private) IP address")
    private String internalIpAddress;
}
