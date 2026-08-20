package tech.kayys.aljabr.engine.lifecycle;

/**
 * Transition policy governing how the agent state machine advances based on handler outcomes.
 */
public interface StateTransitionPolicy {

    AgentState evaluate(AgentState currentState, StateResult result, AgentTask task);
}
