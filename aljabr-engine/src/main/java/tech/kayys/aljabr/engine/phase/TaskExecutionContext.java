package tech.kayys.aljabr.engine.phase;

import tech.kayys.aljabr.engine.loop.CodingState;

import tech.kayys.andalus.execution.AgentToolExecutor;
import tech.kayys.andalus.tool.Tool;
import java.util.List;

public record TaskExecutionContext(
    CodingState state,
    AgentToolExecutor executor,
    List<Tool> tools
) {}
