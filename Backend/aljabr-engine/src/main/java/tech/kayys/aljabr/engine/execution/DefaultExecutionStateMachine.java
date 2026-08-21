package tech.kayys.aljabr.engine.execution;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * State machine managing the 15-state lifecycle and coordinating state handlers.
 */
@ApplicationScoped
public class DefaultExecutionStateMachine {

    private static final Logger LOG = Logger.getLogger(DefaultExecutionStateMachine.class);

    @Inject
    Instance<ExecutionStateHandler> stateHandlers;

    public Uni<ExecutionState> step(ExecutionContext context, ExecutionBudgetTracker budgetTracker) {
        if (context == null) {
            return Uni.createFrom().item(ExecutionState.FAILED);
        }

        ExecutionState current = context.currentState();
        if (current.isTerminal() || current == ExecutionState.PAUSED) {
            return Uni.createFrom().item(current);
        }

        if (budgetTracker != null) {
            budgetTracker.recordStep();
            BudgetStatus status = budgetTracker.evaluate(context.budget());
            if (!status.withinBudget()) {
                LOG.warnf("Execution [%s] exhausted budget: %s",
                    context.executionId().value(), status.exhaustionReason().orElse("Exceeded"));
                return Uni.createFrom().item(ExecutionState.FAILED);
            }
        }

        // Find registered handler for this state if present
        for (ExecutionStateHandler handler : stateHandlers) {
            if (handler.supportedState() == current) {
                return handler.handle(context);
            }
        }

        // Default deterministic state transition graph
        ExecutionState next = switch (current) {
            case CREATED -> ExecutionState.ANALYZING;
            case ANALYZING -> ExecutionState.BUILDING_CONTEXT;
            case BUILDING_CONTEXT -> ExecutionState.THINKING;
            case THINKING -> ExecutionState.AWAITING_ACTION;
            case AWAITING_ACTION -> ExecutionState.VALIDATING_ACTION;
            case VALIDATING_ACTION -> ExecutionState.AUTHORIZING_ACTION;
            case AUTHORIZING_ACTION -> ExecutionState.EXECUTING;
            case EXECUTING -> ExecutionState.OBSERVING;
            case OBSERVING -> ExecutionState.VERIFYING;
            case VERIFYING -> ExecutionState.COMPLETED;
            case REPLANNING -> ExecutionState.ANALYZING;
            case COMPLETED, FAILED, CANCELLED, PAUSED -> current;
        };

        LOG.debugf("Execution [%s] transitioned from %s to %s", context.executionId().value(), current, next);
        return Uni.createFrom().item(next);
    }
}
