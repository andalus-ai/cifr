package tech.kayys.aljabr.engine.orchestrator.output;

import java.nio.file.Path;
import java.time.Duration;

public record OutputSummary(
    int exitCode,
    Duration duration,
    int lineCount,
    String snippet,
    Path logPath
) {}
