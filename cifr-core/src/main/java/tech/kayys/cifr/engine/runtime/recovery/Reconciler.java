package tech.kayys.cifr.engine.runtime.recovery;

import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.engine.runtime.state.ExecutionRuntimeState;

import java.nio.file.Path;

/**
 * Domain-specific reconciler comparing expected execution changes with external reality.
 */
public interface Reconciler {

    String domain();

    Uni<ReconciliationResult> reconcile(ExecutionRuntimeState execution, Path workspaceRoot);
}
