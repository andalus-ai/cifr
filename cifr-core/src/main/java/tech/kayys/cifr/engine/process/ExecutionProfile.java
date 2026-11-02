package tech.kayys.cifr.engine.process;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record ExecutionProfile(
    List<String> command,
    Path workingDirectory,
    Map<String, String> environment,
    ExecutionPolicy executionPolicy
) {}
