package tech.kayys.cifr.engine.plan;

public record AdaptivePolicy(
    boolean autoEscalateVerification,
    boolean progressiveWideningObservation,
    int maxReplansBeforeBlocked
) {
    public static AdaptivePolicy defaultPolicy() {
        return new AdaptivePolicy(true, true, 3);
    }
}
