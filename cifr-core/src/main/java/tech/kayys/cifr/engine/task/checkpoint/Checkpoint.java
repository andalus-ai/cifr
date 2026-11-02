package tech.kayys.cifr.engine.task.checkpoint;

import tech.kayys.cifr.engine.task.TaskId;
import tech.kayys.cifr.engine.task.TaskPhase;
import tech.kayys.cifr.engine.task.snapshot.StateSnapshot;

import java.time.Instant;

public record Checkpoint(
    CheckpointId id,
    TaskId taskId,
    long revision,
    TaskPhase phase,
    Instant createdAt,
    StateSnapshot snapshot
) {
    public static Checkpoint of(TaskId taskId, long revision, TaskPhase phase, StateSnapshot snapshot) {
        return new Checkpoint(CheckpointId.generate(), taskId, revision, phase, Instant.now(), snapshot);
    }
}
