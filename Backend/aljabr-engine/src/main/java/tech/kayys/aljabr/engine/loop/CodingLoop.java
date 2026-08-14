package tech.kayys.aljabr.engine.loop;

import io.smallrye.mutiny.Uni;
import tech.kayys.wayang.agent.AgentResponse;
import tech.kayys.wayang.execution.AgentToolExecutor;
import tech.kayys.wayang.tool.Tool;

import java.util.List;

public interface CodingLoop {
    Uni<AgentResponse> execute(CodingTask task, AgentToolExecutor executor, List<Tool> tools);
}
