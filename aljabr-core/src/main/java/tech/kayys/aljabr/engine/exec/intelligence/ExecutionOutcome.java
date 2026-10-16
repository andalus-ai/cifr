package tech.kayys.aljabr.engine.exec.intelligence;

import java.util.Optional;

public record ExecutionOutcome(
    String executionId,
    ProcessResult rawResult,
    NormalizedOutput normalizedOutput,
    FailureAnalysis failureAnalysis,
    OutputSummary summary,
    Optional<FailureFingerprint> fingerprint
) {
    public boolean isSuccess() {
        return rawResult.isSuccess();
    }
}
