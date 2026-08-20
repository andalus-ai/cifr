package tech.kayys.aljabr.engine.task.reconcile;

import io.smallrye.mutiny.Uni;

public interface ExecutionReconciler {
    Uni<RecoveryDecision> reconcile(String executionId, String toolName);
}
