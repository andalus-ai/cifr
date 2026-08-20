package tech.kayys.aljabr.engine.task.checkpoint;

import tech.kayys.aljabr.engine.task.TaskId;
import tech.kayys.aljabr.engine.task.TaskPhase;
import tech.kayys.aljabr.engine.task.snapshot.StateSnapshot;

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
