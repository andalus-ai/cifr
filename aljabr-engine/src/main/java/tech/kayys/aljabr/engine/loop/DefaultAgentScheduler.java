package tech.kayys.aljabr.engine.loop;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.lifecycle.AgentState;
import tech.kayys.aljabr.engine.runtime.state.ActionRuntimeState;
import tech.kayys.aljabr.engine.runtime.state.RuntimeState;

/**
 * Default implementation of {@link AgentScheduler} determining execution flow from runtime state.
 */
@ApplicationScoped
public class DefaultAgentScheduler implements AgentScheduler {

    @Override
    public NextStep schedule(RuntimeState state) {
        if (state == null || state.task() == null) {
            return NextStep.fail("No active task in runtime state");
        }

        if (state.task().lifecycleState() == AgentState.COMPLETE) {
            return NextStep.complete("Task is marked as complete");
        }

        if (state.task().lifecycleState() == AgentState.FAILED) {
            return NextStep.fail("Task is marked as failed");
        }

        // Check for any pending authorized action
        for (ActionRuntimeState action : state.actions().values()) {
            if (action.isAuthorized()) {
                boolean isCompleted = state.executions().values().stream()
                    .anyMatch(exec -> action.authorizedActionId().isPresent() &&
                        action.authorizedActionId().get().equals(exec.actionId()) &&
                        "COMPLETED".equals(exec.state()));
                if (!isCompleted) {
                    return NextStep.executeAction(action.proposalId(), "Executing pending authorized action");
                }
            }
        }

        return NextStep.decisionCycle("Advancing agent decision cycle for state: " + state.task().lifecycleState());
    }
}
