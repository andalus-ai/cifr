package tech.kayys.aljabr.engine.state;

import tech.kayys.aljabr.api.agent.AgentEvent;
import tech.kayys.aljabr.api.agent.AljabrState;

public interface AgentStateMachine {
    TransitionResult transition(AljabrState state, AgentEvent event);
}
