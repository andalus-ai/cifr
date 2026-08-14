package tech.kayys.aljabr.engine.state;

import tech.kayys.aljabr.api.agent.AljabrState;

public record TransitionResult(
    AljabrState nextState,
    boolean isValid,
    String reason
) {}
