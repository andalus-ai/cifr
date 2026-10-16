package tech.kayys.aljabr.engine.event;

import tech.kayys.aljabr.api.agent.AgentEvent;

public interface EventLedger {
    void record(AgentEvent event);
}
