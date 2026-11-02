package tech.kayys.cifr.engine.tool.policy;
import tech.kayys.cifr.engine.tool.result.ToolFailure;
public interface ToolRetryPolicy {
    RetryDecision evaluate(ToolFailure failure, int attemptNumber);
}
