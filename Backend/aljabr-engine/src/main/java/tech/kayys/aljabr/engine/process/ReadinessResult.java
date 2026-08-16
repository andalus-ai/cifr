package tech.kayys.aljabr.engine.process;

import java.util.Optional;

public record ReadinessResult(
    boolean isReady,
    Optional<String> message
) {
    public static ReadinessResult ready() {
        return new ReadinessResult(true, Optional.empty());
    }

    public static ReadinessResult notReady(String reason) {
        return new ReadinessResult(false, Optional.of(reason));
    }
}
