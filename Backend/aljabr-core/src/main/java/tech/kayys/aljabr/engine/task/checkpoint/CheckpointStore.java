package tech.kayys.aljabr.engine.task.checkpoint;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.task.TaskId;

import java.util.List;
import java.util.Optional;

public interface CheckpointStore {
    Uni<Void> save(Checkpoint checkpoint);
    Uni<Optional<Checkpoint>> latest(TaskId taskId);
    Uni<List<Checkpoint>> list(TaskId taskId);
}
