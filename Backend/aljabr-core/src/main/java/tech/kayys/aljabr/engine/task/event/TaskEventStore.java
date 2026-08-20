package tech.kayys.aljabr.engine.task.event;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.task.TaskId;

public interface TaskEventStore {
    Uni<Void> append(TaskEvent event);
    Multi<TaskEvent> stream(TaskId taskId);
    Multi<TaskEvent> since(TaskId taskId, long afterRevision);
}
