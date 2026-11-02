package tech.kayys.cifr.engine.runtime.recovery;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.cifr.engine.lifecycle.RecoveryDecision;
import tech.kayys.cifr.engine.runtime.state.ExecutionRuntimeState;
import tech.kayys.cifr.engine.runtime.state.RuntimeState;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Default implementation of {@link RecoveryManager}.
 * Coordinates crash inspection and transactional reconciliation.
 */
@ApplicationScoped
public class DefaultRecoveryManager implements RecoveryManager {

    @Inject
    FileReconciler fileReconciler;

    public DefaultRecoveryManager() {}

    public DefaultRecoveryManager(FileReconciler fileReconciler) {
        this.fileReconciler = fileReconciler;
    }

    @Override
    public RecoveryDecision inspect(RuntimeState state) {
        if (state == null) {
            return RecoveryDecision.ABORT;
        }

        boolean hasUnfinishedExecution = state.executions().values().stream()
            .anyMatch(exec -> "RUNNING".equals(exec.state()) && !exec.isReconciled());

        if (hasUnfinishedExecution) {
            return RecoveryDecision.REPAIR;
        }

        return RecoveryDecision.RETRY;
    }

    @Override
    public Uni<RuntimeState> recover(RuntimeState state, Path workspaceRoot) {
        return Uni.createFrom().item(() -> {
            if (state == null) {
                return state;
            }

            Map<String, ExecutionRuntimeState> reconciledExecs = new HashMap<>(state.executions());
            for (Map.Entry<String, ExecutionRuntimeState> entry : state.executions().entrySet()) {
                ExecutionRuntimeState exec = entry.getValue();
                if ("RUNNING".equals(exec.state()) && !exec.isReconciled()) {
                    ReconciliationResult result = fileReconciler.reconcile(exec, workspaceRoot).await().indefinitely();
                    if (result.isReconciled()) {
                        reconciledExecs.put(entry.getKey(), exec.reconcile(result.realityStatus()));
                    }
                }
            }

            return new RuntimeState(
                state.task(),
                state.plan(),
                state.steps(),
                state.actions(),
                reconciledExecs,
                state.evidenceIds()
            );
        });
    }
}
