package tech.kayys.aljabr.engine.tool.policy;
import tech.kayys.aljabr.engine.tool.protocol.ToolFailureCategory;
import tech.kayys.aljabr.engine.tool.result.ToolFailure;
public class DefaultToolRetryPolicy implements ToolRetryPolicy {
    private static final int MAX_RETRIES = 3;
    @Override
    public RetryDecision evaluate(ToolFailure failure, int attemptNumber) {
        if (attemptNumber >= MAX_RETRIES) return RetryDecision.no("Max retries exceeded");
        return switch (failure.category()) {
            case NETWORK -> RetryDecision.yes(1000L * attemptNumber);
            case TIMEOUT -> RetryDecision.yes(2000L);
            case INVALID_INPUT, PERMISSION, NOT_FOUND -> RetryDecision.no("Non-retryable: " + failure.category());
            default -> RetryDecision.no("Unknown failure");
        };
    }
}
