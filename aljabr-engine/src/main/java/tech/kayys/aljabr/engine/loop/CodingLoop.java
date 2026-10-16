package tech.kayys.aljabr.engine.loop;

import io.smallrye.mutiny.Uni;
import tech.kayys.andalus.agent.AgentResponse;
import tech.kayys.andalus.execution.AgentToolExecutor;
import tech.kayys.andalus.tool.Tool;

import java.util.List;

public interface CodingLoop {
    Uni<AgentResponse> execute(CodingTask task, AgentToolExecutor executor, List<Tool> tools);
}
