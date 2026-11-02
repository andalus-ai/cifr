package tech.kayys.cifr.engine.state;

import tech.kayys.cifr.api.agent.CifrState;

public record TransitionResult(
    CifrState nextState,
    boolean isValid,
    String reason
) {}
