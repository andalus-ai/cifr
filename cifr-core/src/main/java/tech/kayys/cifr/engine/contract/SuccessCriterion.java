package tech.kayys.cifr.engine.contract;

/**
 * Concrete success criterion required by a GoalContract.
 */
public record SuccessCriterion(
    String id,
    CriterionType type,
    String description,
    boolean isMandatory
) {
    public static SuccessCriterion mandatory(String id, CriterionType type, String description) {
        return new SuccessCriterion(id, type, description, true);
    }

    public static SuccessCriterion optional(String id, CriterionType type, String description) {
        return new SuccessCriterion(id, type, description, false);
    }
}
