package tech.kayys.aljabr.engine.runtime.recovery;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.runtime.state.ExecutionRuntimeState;

import java.nio.file.Path;

/**
 * Domain-specific reconciler comparing expected execution changes with external reality.
 */
public interface Reconciler {

    String domain();

    Uni<ReconciliationResult> reconcile(ExecutionRuntimeState execution, Path workspaceRoot);
}
