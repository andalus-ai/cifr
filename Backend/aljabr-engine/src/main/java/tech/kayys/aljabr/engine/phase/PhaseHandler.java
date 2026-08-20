package tech.kayys.aljabr.engine.phase;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.api.agent.AgentEvent;
import tech.kayys.aljabr.api.agent.AgentPhase;
import tech.kayys.aljabr.engine.loop.CodingState;
import tech.kayys.wayang.execution.AgentToolExecutor;
import tech.kayys.wayang.tool.Tool;

import java.util.List;

/**
 * Strategy SPI for executing an individual agent phase within the coding loop.
 */
public interface PhaseHandler {
    boolean supports(AgentPhase phase);
    Uni<AgentEvent> handle(CodingState state, AgentToolExecutor executor, List<Tool> tools);
}
