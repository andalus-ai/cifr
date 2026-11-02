package tech.kayys.cifr.engine.lifecycle;

import io.smallrye.mutiny.Uni;

/**
 * Handler interface executed when the agent is inside a specific lifecycle state.
 */
public interface AgentStateHandler {

    AgentState handledState();

    Uni<StateResult> execute(AgentStateContext context);
}
