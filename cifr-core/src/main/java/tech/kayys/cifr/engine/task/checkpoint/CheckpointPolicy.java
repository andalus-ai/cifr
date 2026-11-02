package tech.kayys.cifr.engine.task.checkpoint;

import tech.kayys.cifr.engine.task.TaskState;
import tech.kayys.cifr.engine.task.event.TaskEvent;

public interface CheckpointPolicy {
    boolean shouldCheckpoint(TaskEvent event, TaskState state);
}
