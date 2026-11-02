package tech.kayys.cifr.engine.tool.policy;
public record RetryDecision(boolean shouldRetry, long delayMs, String reason) {
    public static RetryDecision yes(long delayMs) { return new RetryDecision(true, delayMs, "Retryable error"); }
    public static RetryDecision no(String reason) { return new RetryDecision(false, 0, reason); }
}
