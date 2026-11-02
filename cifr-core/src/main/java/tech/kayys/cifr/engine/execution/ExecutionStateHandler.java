package tech.kayys.cifr.engine.execution;

import io.smallrye.mutiny.Uni;

/**
 * SPI handler for executing state-specific logic during execution state transitions.
 */
public interface ExecutionStateHandler {
    ExecutionState supportedState();
    Uni<ExecutionState> handle(ExecutionContext context);
}
