package tech.kayys.aljabr.engine.memory;

/**
 * Memory confidence score paired with its epistemic basis.
 */
public record MemoryConfidence(
    double score,
    ConfidenceBasis basis
) {
    public static MemoryConfidence userStated() {
        return new MemoryConfidence(1.0, ConfidenceBasis.USER_STATED);
    }

    public static MemoryConfidence toolVerified(double score) {
        return new MemoryConfidence(score, ConfidenceBasis.TOOL_VERIFIED);
    }

    public static MemoryConfidence inferred(double score) {
        return new MemoryConfidence(score, ConfidenceBasis.MODEL_INFERENCE);
    }
}
