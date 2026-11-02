package tech.kayys.cifr.engine.loop;

import java.util.List;

/**
 * Claim made by the model that the task's GoalContract has been satisfied.
 */
public record CompletionClaim(
    boolean isClaimingSuccess,
    String summary,
    List<String> satisfiedCriterionIds
) {
    public CompletionClaim {
        satisfiedCriterionIds = satisfiedCriterionIds != null ? List.copyOf(satisfiedCriterionIds) : List.of();
    }

    public static CompletionClaim success(String summary) {
        return new CompletionClaim(true, summary, List.of());
    }
}
