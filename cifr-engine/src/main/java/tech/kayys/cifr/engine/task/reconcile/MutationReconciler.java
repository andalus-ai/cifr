package tech.kayys.cifr.engine.task.reconcile;

import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.engine.task.TaskState;
import tech.kayys.cifr.engine.task.event.TaskEvent;

import java.util.List;

public interface MutationReconciler {
    Uni<RecoveryDecision> reconcile(
        TaskState state,
        String workspaceRevision,
        List<TaskEvent> recentEvents
    );
}
