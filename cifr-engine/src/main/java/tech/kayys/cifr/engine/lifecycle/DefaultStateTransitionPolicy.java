package tech.kayys.cifr.engine.lifecycle;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Default implementation of {@link StateTransitionPolicy}.
 * Transitions states deterministically based on handler outcome.
 */
@ApplicationScoped
public class DefaultStateTransitionPolicy implements StateTransitionPolicy {

    @Override
    public AgentState evaluate(AgentState currentState, StateResult result, AgentTask task) {
        if (result == null || result.outcome() == null) {
            return currentState;
        }

        return switch (currentState) {
            case INTAKE -> AgentState.CLASSIFY;
            case CLASSIFY -> AgentState.UNDERSTAND;
            case UNDERSTAND -> {
                if (result.outcome() == StateOutcome.NEEDS_RETRIEVAL) yield AgentState.RETRIEVE;
                yield AgentState.PLAN;
            }
            case RETRIEVE -> AgentState.PLAN;
            case PLAN -> {
                if (result.outcome() == StateOutcome.NEEDS_RETRIEVAL) yield AgentState.RETRIEVE;
                yield AgentState.EXECUTE;
            }
            case EXECUTE -> AgentState.OBSERVE;
            case OBSERVE -> AgentState.VERIFY;
            case VERIFY -> {
                if (result.outcome() == StateOutcome.SUCCESS) yield AgentState.COMPLETE;
                if (result.outcome() == StateOutcome.RETRYABLE_FAILURE) yield AgentState.REPAIR;
                yield AgentState.FAILED;
            }
            case REPAIR -> AgentState.RETRIEVE;
            case COMPLETE, FAILED, CANCELLED -> currentState;
        };
    }
}
