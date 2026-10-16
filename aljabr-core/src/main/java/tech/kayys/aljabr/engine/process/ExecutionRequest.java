package tech.kayys.aljabr.engine.process;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record ExecutionRequest(
    String id,
    List<String> command,
    Path workingDirectory,
    Map<String, String> environment,
    ExecutionPolicy policy
) {}
