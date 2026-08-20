package tech.kayys.aljabr.engine.task.snapshot;

import java.util.Set;

public record ContextState(
    String activeContextSetId,
    Set<String> evidenceIds,
    long tokenBudgetRemaining,
    String summary
) {
    public ContextState {
        evidenceIds = evidenceIds != null ? Set.copyOf(evidenceIds) : Set.of();
    }

    public static ContextState empty() {
        return new ContextState(null, Set.of(), 0L, null);
    }
}
