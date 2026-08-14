package tech.kayys.aljabr.engine.phase;

import tech.kayys.aljabr.engine.loop.CodingState;

import tech.kayys.wayang.execution.AgentToolExecutor;
import tech.kayys.wayang.tool.Tool;
import java.util.List;

public record TaskExecutionContext(
    CodingState state,
    AgentToolExecutor executor,
    List<Tool> tools
) {}
