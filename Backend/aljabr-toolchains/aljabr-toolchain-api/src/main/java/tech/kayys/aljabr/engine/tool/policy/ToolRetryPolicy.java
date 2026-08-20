package tech.kayys.aljabr.engine.tool.policy;
import tech.kayys.aljabr.engine.tool.result.ToolFailure;
public interface ToolRetryPolicy {
    RetryDecision evaluate(ToolFailure failure, int attemptNumber);
}
