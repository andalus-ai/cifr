package tech.kayys.aljabr.engine.process;

import java.time.Instant;
import java.util.Optional;

public record ProcessRecord(
    ProcessId id,
    String taskId,
    String workspaceId,
    String commandSummary,
    ProcessState state,
    Instant startedAt,
    Optional<Instant> endedAt,
    Optional<Integer> exitCode
) {}
