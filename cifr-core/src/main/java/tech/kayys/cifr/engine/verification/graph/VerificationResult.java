package tech.kayys.cifr.engine.verification.graph;

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
