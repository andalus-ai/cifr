package tech.kayys.cifr.engine.gate;

import tech.kayys.cifr.engine.loop.CodingState;

public record CodingExecutionContext(
    CodingState state
) {}
