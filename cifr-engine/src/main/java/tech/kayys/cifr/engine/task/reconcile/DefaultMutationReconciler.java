package tech.kayys.cifr.engine.task.reconcile;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.task.TaskPhase;
import tech.kayys.cifr.engine.task.TaskState;
import tech.kayys.cifr.engine.task.event.TaskEvent;
import tech.kayys.cifr.engine.task.event.TaskEventType;

import java.util.List;

@ApplicationScoped
public class DefaultMutationReconciler implements MutationReconciler {

    @Override
    public Uni<RecoveryDecision> reconcile(TaskState state, String workspaceRevision, List<TaskEvent> recentEvents) {
        if (recentEvents == null) {
            return Uni.createFrom().item(RecoveryDecision.CONTINUE);
        }

        boolean patchStarted = recentEvents.stream().anyMatch(e -> e.type() == TaskEventType.PATCH_APPLIED);
        boolean patchCompleted = recentEvents.stream().anyMatch(e -> e.type() == TaskEventType.TOOL_COMPLETED);

        if (state != null && state.phase() == TaskPhase.PATCHING) {
            if (patchCompleted) {
                return Uni.createFrom().item(RecoveryDecision.CONTINUE);
            }
            if (patchStarted) {
                return Uni.createFrom().item(RecoveryDecision.ROLLBACK);
            }
        }
        return Uni.createFrom().item(RecoveryDecision.CONTINUE);
    }
}
