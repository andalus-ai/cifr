package tech.kayys.cifr.engine.event;

import tech.kayys.cifr.api.agent.AgentEvent;

public interface EventLedger {
    void record(AgentEvent event);
}
