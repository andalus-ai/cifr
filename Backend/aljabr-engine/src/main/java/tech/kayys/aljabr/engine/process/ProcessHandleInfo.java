package tech.kayys.aljabr.engine.process;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record ProcessHandleInfo(
    ProcessId id,
    String executable,
    List<String> arguments,
    ProcessState state,
    Instant startedAt,
    Optional<Instant> endedAt,
    Optional<Integer> exitCode
) {
    public ProcessHandleInfo {
        arguments = arguments != null ? List.copyOf(arguments) : List.of();
    }
}
