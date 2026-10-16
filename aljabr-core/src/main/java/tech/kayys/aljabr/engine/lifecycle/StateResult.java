package tech.kayys.aljabr.engine.lifecycle;

import java.util.List;
import java.util.Optional;

/**
 * Structured result returned by a state handler upon completing an iteration.
 */
public record StateResult(
    StateOutcome outcome,
    List<String> evidenceIds,
    List<String> eventIds,
    Optional<String> details
) {
    public StateResult {
        evidenceIds = evidenceIds != null ? List.copyOf(evidenceIds) : List.of();
        eventIds = eventIds != null ? List.copyOf(eventIds) : List.of();
    }

    public static StateResult success(String details) {
        return new StateResult(StateOutcome.SUCCESS, List.of(), List.of(), Optional.ofNullable(details));
    }

    public static StateResult successWithEvidence(List<String> evidenceIds) {
        return new StateResult(StateOutcome.SUCCESS, evidenceIds, List.of(), Optional.empty());
    }

    public static StateResult outcome(StateOutcome outcome, String reason) {
        return new StateResult(outcome, List.of(), List.of(), Optional.ofNullable(reason));
    }
}
