package tech.kayys.cifr.engine.loop;

import tech.kayys.cifr.engine.runtime.state.RuntimeState;

/**
 * Central controller determining what happens next in the agent runtime without requiring model calls.
 */
public interface AgentScheduler {

    NextStep schedule(RuntimeState state);
}
