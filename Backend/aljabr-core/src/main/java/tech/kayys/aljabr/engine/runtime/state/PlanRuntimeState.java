package tech.kayys.aljabr.engine.runtime.state;

import java.util.List;

/**
 * Materialized plan state derived from events.
 */
public record PlanRuntimeState(
    String activePlanId,
    int stepCount,
    List<String> objectives
) {
    public PlanRuntimeState {
        objectives = objectives != null ? List.copyOf(objectives) : List.of();
    }

    public static PlanRuntimeState empty() {
        return new PlanRuntimeState(null, 0, List.of());
    }
}
