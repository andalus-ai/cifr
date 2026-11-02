package tech.kayys.cifr.engine.capability;

/**
 * Postcondition assertion evaluated after capability execution.
 */
public record Postcondition(
    String id,
    String assertionExpression
) {
    public static Postcondition of(String id, String expr) {
        return new Postcondition(id, expr);
    }
}
