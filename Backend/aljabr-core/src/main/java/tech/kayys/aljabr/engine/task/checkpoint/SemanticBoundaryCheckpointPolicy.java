package tech.kayys.aljabr.engine.task.checkpoint;

import tech.kayys.aljabr.engine.task.TaskState;
import tech.kayys.aljabr.engine.task.event.TaskEvent;
import tech.kayys.aljabr.engine.task.event.TaskEventType;

import java.util.Set;

public class SemanticBoundaryCheckpointPolicy implements CheckpointPolicy {
    private static final Set<TaskEventType> CHECKPOINT_EVENTS = Set.of(
        TaskEventType.PHASE_CHANGED,
        TaskEventType.PLAN_CREATED,
        TaskEventType.PATCH_APPLIED,
        TaskEventType.TEST_COMPLETED,
        TaskEventType.TASK_COMPLETED,
        TaskEventType.TOOL_COMPLETED
    );

    @Override
    public boolean shouldCheckpoint(TaskEvent event, TaskState state) {
        return event != null && CHECKPOINT_EVENTS.contains(event.type());
    }
}
