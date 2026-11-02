package tech.kayys.cifr.engine.memory;

/**
 * Explicit justification and score contributor for a memory retrieval match.
 */
public record MatchReason(
    String description,
    double weight
) {
    public static MatchReason of(String description, double weight) {
        return new MatchReason(description, weight);
    }
}
