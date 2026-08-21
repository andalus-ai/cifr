package tech.kayys.aljabr.engine.runtime.recovery;

import tech.kayys.aljabr.engine.runtime.state.ExecutionRuntimeState;

import java.util.Optional;

/**
 * Result of reconciling an in-flight execution transaction against external filesystem/process reality.
 */
public record ReconciliationResult(
    boolean isReconciled,
    String realityStatus,
    Optional<String> generatedEvidenceSummary
) {
    public static ReconciliationResult matched(String status, String evidenceSummary) {
        return new ReconciliationResult(true, status, Optional.of(evidenceSummary));
    }

    public static ReconciliationResult unknown(String reason) {
        return new ReconciliationResult(false, "UNKNOWN: " + reason, Optional.empty());
    }
}
