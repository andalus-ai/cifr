package tech.kayys.cifr.engine.task.reconcile;

import io.smallrye.mutiny.Uni;

public interface ExecutionReconciler {
    Uni<RecoveryDecision> reconcile(String executionId, String toolName);
}
