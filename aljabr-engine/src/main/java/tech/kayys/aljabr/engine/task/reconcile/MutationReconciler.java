package tech.kayys.aljabr.engine.task.reconcile;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.task.TaskState;
import tech.kayys.aljabr.engine.task.event.TaskEvent;

import java.util.List;

public interface MutationReconciler {
    Uni<RecoveryDecision> reconcile(
        TaskState state,
        String workspaceRevision,
        List<TaskEvent> recentEvents
    );
}
