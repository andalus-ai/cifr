package tech.kayys.aljabr.engine.tool;

import java.util.Optional;
import tech.kayys.aljabr.engine.loop.CodingPolicy;

public record ToolExecutionContext(
    String taskId,
    String workspaceId,
    CodingPolicy policy,
    Optional<String> checkpoint,
    Runnable cancellation
) {}
