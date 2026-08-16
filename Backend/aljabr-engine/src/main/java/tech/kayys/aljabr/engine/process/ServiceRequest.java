package tech.kayys.aljabr.engine.process;

import java.nio.file.Path;
import java.util.List;

public record ServiceRequest(
    String executable,
    List<String> arguments,
    Path workingDirectory,
    List<Integer> expectedPorts
) {
    public ServiceRequest {
        arguments = arguments != null ? List.copyOf(arguments) : List.of();
        expectedPorts = expectedPorts != null ? List.copyOf(expectedPorts) : List.of();
    }
}
