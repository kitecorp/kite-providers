package cloud.kitelang.provider.terraform;

/**
 * Protocol-neutral Terraform diagnostic, decoupled from the generated
 * tfplugin5/tfplugin6 message classes so handlers can process diagnostics
 * without knowing which protocol version produced them.
 *
 * @param severity the diagnostic severity
 * @param summary  short human-readable problem summary
 * @param detail   optional longer description (empty string when absent)
 */
public record TfDiagnostic(Severity severity, String summary, String detail) {

    /** Mirrors the {@code Diagnostic.Severity} enum shared by both protocol versions. */
    public enum Severity {
        INVALID,
        ERROR,
        WARNING
    }
}
