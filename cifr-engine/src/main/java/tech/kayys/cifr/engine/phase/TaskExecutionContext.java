package tech.kayys.cifr.engine.phase;

import tech.kayys.cifr.engine.loop.CodingState;

import tech.kayys.andalus.execution.AgentToolExecutor;
import tech.kayys.andalus.tool.Tool;
import java.util.List;

public record TaskExecutionContext(
    CodingState state,
    AgentToolExecutor executor,
    List<Tool> tools
) {}
