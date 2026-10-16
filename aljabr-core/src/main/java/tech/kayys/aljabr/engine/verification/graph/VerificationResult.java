package tech.kayys.aljabr.engine.verification.graph;

public record VerificationResult(
    VerificationCheckId id,
    VerificationStatus status,
    VerificationEvidence evidence,
    VerificationConfidence confidence
) {
    public boolean isPassed() {
        return status == VerificationStatus.PASSED;
    }
}
