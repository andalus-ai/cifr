package tech.kayys.aljabr.engine.task.step;

import java.time.Instant;

public record StepExecution(
    StepId stepId,
    StepStatus status,
    Instant startedAt,
    Instant completedAt,
    String inputHash,
    String outputHash
) {}
