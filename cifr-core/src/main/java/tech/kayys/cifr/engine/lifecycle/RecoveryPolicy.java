package tech.kayys.cifr.engine.lifecycle;

/**
 * Recovery policy determining how the agent should react to task-level failures.
 */
public interface RecoveryPolicy {

    RecoveryDecision decide(TaskFailure failure, AgentTask task);
}
