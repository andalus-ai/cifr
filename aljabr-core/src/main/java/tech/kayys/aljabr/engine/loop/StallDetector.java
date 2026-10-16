package tech.kayys.aljabr.engine.loop;

import tech.kayys.aljabr.engine.runtime.state.RuntimeState;

/**
 * Detects infinite loops, repeated action oscillations, or lack of progress in the agent runtime.
 */
public interface StallDetector {

    StallResult inspect(RuntimeState state);
}
