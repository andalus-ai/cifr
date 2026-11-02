package tech.kayys.cifr.engine.state;

import tech.kayys.cifr.api.agent.AgentEvent;
import tech.kayys.cifr.api.agent.CifrState;

public interface AgentStateMachine {
    TransitionResult transition(CifrState state, AgentEvent event);
}
