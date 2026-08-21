package tech.kayys.aljabr.engine.capability;

/**
 * Precondition requirement validated before capability execution.
 */
public record Precondition(
    String id,
    String conditionExpression,
    boolean isMandatory
) {
    public static Precondition mandatory(String id, String expr) {
        return new Precondition(id, expr, true);
    }
}
