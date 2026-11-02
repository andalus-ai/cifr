package tech.kayys.cifr.engine.contract;

/**
 * An explicit constraint bounding agent action proposals and workspace modifications.
 */
public record Constraint(
    String id,
    ConstraintType type,
    String description
) {
    public static Constraint of(String id, ConstraintType type, String description) {
        return new Constraint(id, type, description);
    }
}
