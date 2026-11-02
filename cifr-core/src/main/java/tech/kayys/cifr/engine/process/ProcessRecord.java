package tech.kayys.cifr.engine.process;

import java.time.Instant;
import java.util.List;

public record ProcessRecord(
    ProcessId id,
    long pid,
    List<String> command,
    ProcessState state,
    Instant startedAt,
    Instant finishedAt,
    int exitCode
) {}
