package tech.kayys.cifr.engine.task.runtime;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.cifr.engine.task.TaskId;
import tech.kayys.cifr.engine.task.TaskState;
import tech.kayys.cifr.engine.task.checkpoint.CheckpointStore;
import tech.kayys.cifr.engine.task.event.TaskEventStore;

@ApplicationScoped
public class DefaultResumeEngine implements ResumeEngine {

    @Inject
    CheckpointStore checkpointStore;

    @Inject
    TaskEventStore eventStore;

    @Override
    public Uni<TaskState> resume(TaskId taskId) {
        return checkpointStore.latest(taskId)
            .map(opt -> opt.map(cp -> cp.snapshot().state()).orElse(null));
    }

    @Override
    public Uni<Boolean> canResume(TaskId taskId) {
        return checkpointStore.latest(taskId).map(opt -> opt.isPresent());
    }
}
