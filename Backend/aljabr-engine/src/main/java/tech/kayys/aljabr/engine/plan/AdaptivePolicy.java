package tech.kayys.aljabr.engine.plan;

public record AdaptivePolicy(
    boolean autoEscalateVerification,
    boolean progressiveWideningObservation,
    int maxReplansBeforeBlocked
) {
    public static AdaptivePolicy defaultPolicy() {
        return new AdaptivePolicy(true, true, 3);
    }
}
