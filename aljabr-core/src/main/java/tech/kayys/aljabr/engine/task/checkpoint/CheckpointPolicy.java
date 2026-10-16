package tech.kayys.aljabr.engine.task.checkpoint;

import tech.kayys.aljabr.engine.task.TaskState;
import tech.kayys.aljabr.engine.task.event.TaskEvent;

public interface CheckpointPolicy {
    boolean shouldCheckpoint(TaskEvent event, TaskState state);
}
