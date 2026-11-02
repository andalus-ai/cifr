package tech.kayys.cifr.engine.loop;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.action.ActionResultStatus;
import tech.kayys.cifr.engine.contract.GoalContract;
import tech.kayys.cifr.engine.contract.SuccessCriterion;
import tech.kayys.cifr.engine.runtime.state.RuntimeState;

/**
 * Default implementation of {@link CompletionEvaluator} evaluating GoalContract criteria against state.
 */
@ApplicationScoped
public class DefaultCompletionEvaluator implements CompletionEvaluator {

    @Override
    public CompletionResult evaluate(GoalContract contract, RuntimeState state) {
        if (contract == null) {
            return CompletionResult.completed("No goal contract constraints specified");
        }

        if (state == null) {
            return CompletionResult.notCompleted("State is not initialized");
        }

        // Check if any mandatory criterion failed based on execution status
        for (SuccessCriterion criterion : contract.successCriteria()) {
            if (criterion.isMandatory()) {
                boolean hasExecutionFailure = state.executions().values().stream()
                    .anyMatch(exec -> exec.resultStatus().isPresent() &&
                        exec.resultStatus().get() == ActionResultStatus.FAILED);
                if (hasExecutionFailure) {
                    return CompletionResult.notCompleted("Mandatory criterion not met: " + criterion.description());
                }
            }
        }

        return CompletionResult.completed("All contract criteria satisfied");
    }
}
