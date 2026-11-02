package tech.kayys.cifr.engine.runtime.state;

import tech.kayys.cifr.engine.runtime.event.RuntimeEvent;

/**
 * Pure, deterministic, side-effect free reducer that applies a runtime event to compute the next RuntimeState.
 */
public interface StateReducer {

    RuntimeState apply(RuntimeState current, RuntimeEvent event);
}
