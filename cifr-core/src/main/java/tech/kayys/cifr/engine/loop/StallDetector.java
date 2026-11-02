package tech.kayys.cifr.engine.loop;

import tech.kayys.cifr.engine.runtime.state.RuntimeState;

/**
 * Detects infinite loops, repeated action oscillations, or lack of progress in the agent runtime.
 */
public interface StallDetector {

    StallResult inspect(RuntimeState state);
}
