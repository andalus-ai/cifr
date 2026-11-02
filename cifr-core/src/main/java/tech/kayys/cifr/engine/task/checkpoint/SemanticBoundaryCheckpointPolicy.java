package tech.kayys.cifr.engine.task.checkpoint;

import tech.kayys.cifr.engine.task.TaskState;
import tech.kayys.cifr.engine.task.event.TaskEvent;
import tech.kayys.cifr.engine.task.event.TaskEventType;

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
