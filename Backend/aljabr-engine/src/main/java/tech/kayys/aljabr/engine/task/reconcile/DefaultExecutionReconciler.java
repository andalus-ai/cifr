package tech.kayys.aljabr.engine.task.reconcile;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DefaultExecutionReconciler implements ExecutionReconciler {

    @Override
    public Uni<RecoveryDecision> reconcile(String executionId, String toolName) {
        return Uni.createFrom().item(RecoveryDecision.CONTINUE);
    }
}
