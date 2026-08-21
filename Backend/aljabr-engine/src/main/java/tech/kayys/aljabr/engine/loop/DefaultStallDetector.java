package tech.kayys.aljabr.engine.loop;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.action.ActionResultStatus;
import tech.kayys.aljabr.engine.runtime.state.RuntimeState;

/**
 * Default implementation of {@link StallDetector} inspecting state for infinite loops or repeated errors.
 */
@ApplicationScoped
public class DefaultStallDetector implements StallDetector {

    @Override
    public StallResult inspect(RuntimeState state) {
        if (state == null) {
            return StallResult.ok();
        }

        long failureCount = state.executions().values().stream()
            .filter(exec -> exec.resultStatus().isPresent() && exec.resultStatus().get() == ActionResultStatus.FAILED)
            .count();

        if (failureCount > 5) {
            return StallResult.stalled("Consecutive failure threshold exceeded: " + failureCount);
        }

        return StallResult.ok();
    }
}
