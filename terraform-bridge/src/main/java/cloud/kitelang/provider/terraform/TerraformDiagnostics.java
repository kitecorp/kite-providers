package cloud.kitelang.provider.terraform;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Shared diagnostics policy for Terraform RPC responses: WARNING-level entries
 * are logged, ERROR-level entries abort the operation. Centralised here so the
 * handlers and the bridge provider apply identical behaviour.
 */
@Slf4j
final class TerraformDiagnostics {

    private TerraformDiagnostics() {
    }

    /**
     * Logs WARNING diagnostics and throws if any ERROR diagnostic is present.
     *
     * @param diagnostics the diagnostics from a TF response
     * @param operation   the operation name for error reporting (e.g. "create", "configure")
     * @param subject     what the operation acted on (TF type name or provider name)
     * @throws RuntimeException if any ERROR-level diagnostic is present
     */
    static void check(List<TfDiagnostic> diagnostics, String operation, String subject) {
        diagnostics.stream()
                .filter(d -> d.severity() == TfDiagnostic.Severity.WARNING)
                .forEach(d -> log.warn("Terraform {} warning for {}: {} — {}",
                        operation, subject, d.summary(), d.detail()));

        var errors = diagnostics.stream()
                .filter(d -> d.severity() == TfDiagnostic.Severity.ERROR)
                .toList();

        if (!errors.isEmpty()) {
            var message = errors.stream()
                    .map(d -> d.summary() + ": " + d.detail())
                    .collect(Collectors.joining("; "));
            throw new RuntimeException("Terraform %s failed: %s".formatted(operation, message));
        }
    }
}
