package tech.kayys.aljabr.engine.gate;

import tech.kayys.aljabr.engine.loop.CodingState;

public record CodingExecutionContext(
    CodingState state
) {}
