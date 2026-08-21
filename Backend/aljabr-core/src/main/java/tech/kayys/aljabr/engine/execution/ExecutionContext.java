package tech.kayys.aljabr.engine.execution;

import java.util.Map;

/**
 * Dynamic execution context provided to state machine handlers.
 */
public record ExecutionContext(
    ExecutionId executionId,
    String taskId,
    String goal,
    ExecutionState currentState,
    ExecutionBudget budget,
    Map<String, Object> attributes
) {
    public ExecutionContext {
        attributes = attributes != null ? Map.copyOf(attributes) : Map.of();
    }

    public ExecutionContext withState(ExecutionState newState) {
        return new ExecutionContext(executionId, taskId, goal, newState, budget, attributes);
    }
}
