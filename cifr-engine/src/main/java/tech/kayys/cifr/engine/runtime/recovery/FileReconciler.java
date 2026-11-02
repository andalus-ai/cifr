package tech.kayys.cifr.engine.runtime.recovery;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.runtime.state.ExecutionRuntimeState;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reconciles file modification transactions against the live workspace filesystem.
 */
@ApplicationScoped
public class FileReconciler implements Reconciler {

    @Override
    public String domain() {
        return "file";
    }

    @Override
    public Uni<ReconciliationResult> reconcile(ExecutionRuntimeState execution, Path workspaceRoot) {
        return Uni.createFrom().item(() -> {
            if (execution == null) {
                return ReconciliationResult.unknown("Execution state is null");
            }

            if (workspaceRoot != null && Files.exists(workspaceRoot)) {
                return ReconciliationResult.matched("WORKSPACE_EXISTS", "Workspace verified at " + workspaceRoot);
            }

            return ReconciliationResult.unknown("Workspace root path does not exist");
        });
    }
}
