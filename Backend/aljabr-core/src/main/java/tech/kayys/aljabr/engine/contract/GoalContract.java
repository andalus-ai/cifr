package tech.kayys.aljabr.engine.contract;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Explicit contract defining task objective, success criteria, constraints, and quality requirements.
 * The runtime owns this contract; the model does not declare completion independently.
 */
public record GoalContract(
    String objective,
    List<SuccessCriterion> successCriteria,
    List<Constraint> constraints,
    QualityRequirement quality,
    Optional<Instant> deadline
) {
    public GoalContract {
        successCriteria = successCriteria != null ? List.copyOf(successCriteria) : List.of();
        constraints = constraints != null ? List.copyOf(constraints) : List.of();
    }

    public static GoalContract of(String objective, List<SuccessCriterion> criteria, List<Constraint> constraints) {
        return new GoalContract(objective, criteria, constraints, QualityRequirement.standard(), Optional.empty());
    }
}
