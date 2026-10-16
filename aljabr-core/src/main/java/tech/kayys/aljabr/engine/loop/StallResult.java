package tech.kayys.aljabr.engine.loop;

/**
 * Inspection result from StallDetector indicating if an agent is oscillating or blocked.
 */
public record StallResult(
    boolean isStalled,
    String reason
) {
    public static StallResult ok() {
        return new StallResult(false, "");
    }

    public static StallResult stalled(String reason) {
        return new StallResult(true, reason);
    }
}
