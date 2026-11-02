package tech.kayys.cifr.engine.phase;

import io.smallrye.mutiny.Uni;
import tech.kayys.cifr.api.agent.AgentEvent;
import tech.kayys.cifr.api.agent.AgentPhase;
import tech.kayys.cifr.engine.loop.CodingState;
import tech.kayys.andalus.execution.AgentToolExecutor;
import tech.kayys.andalus.tool.Tool;

import java.util.List;

/**
 * Strategy SPI for executing an individual agent phase within the coding loop.
 */
public interface PhaseHandler {
    boolean supports(AgentPhase phase);
    Uni<AgentEvent> handle(CodingState state, AgentToolExecutor executor, List<Tool> tools);
}
