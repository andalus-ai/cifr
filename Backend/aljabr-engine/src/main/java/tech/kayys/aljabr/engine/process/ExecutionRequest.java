package tech.kayys.aljabr.engine.process;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public record ExecutionRequest(
    String executable,
    List<String> arguments,
    Path workingDirectory,
    ExecutionProfile profile,
    Duration timeout,
    OutputPolicy outputPolicy
) {
    public ExecutionRequest {
        arguments = arguments != null ? List.copyOf(arguments) : List.of();
    }
}
