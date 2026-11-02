package tech.kayys.cifr.engine.process;

import java.time.Instant;

public record ProcessHandleInfo(
    ProcessId id,
    long pid,
    ProcessState state,
    Instant startTime
) {}
