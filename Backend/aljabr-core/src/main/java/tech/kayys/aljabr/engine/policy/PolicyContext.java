package tech.kayys.aljabr.engine.policy;

import tech.kayys.aljabr.engine.lifecycle.AgentState;
import tech.kayys.aljabr.engine.lifecycle.AgentTask;

/**
 * Context provided for policy evaluation.
 */
public record PolicyContext(
    AgentTask task,
    AgentState state,
    String workspaceRevision,
    String userPreferences
) {
    public static PolicyContext of(AgentTask task) {
        return new PolicyContext(task, task.state(), "rev-1", "default");
    }
}
