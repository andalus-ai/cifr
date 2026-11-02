package tech.kayys.cifr.engine.process;

public record ReadinessResult(
    boolean isReady,
    String message
) {
    public static ReadinessResult ready() {
        return new ReadinessResult(true, "Service is ready");
    }

    public static ReadinessResult notReady(String message) {
        return new ReadinessResult(false, message);
    }
}
