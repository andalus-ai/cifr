package tech.kayys.aljabr.orchestration.strategy;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import tech.kayys.wayang.agent.AgentRequest;
import tech.kayys.wayang.agent.AgentResponse;
import tech.kayys.wayang.tool.Tool;
import tech.kayys.wayang.execution.AgentToolExecutor;

import java.util.List;

/**
 * Interface defining the execution loop strategy for an agent orchestrator.
 */
public interface ExecutionStrategy {

    /**
     * Unique identifier for the strategy (e.g., "react", "plan_and_solve").
     */
    String id();

    /**
     * Executes the agent request to completion, returning the final response.
     */
    Uni<AgentResponse> execute(AgentRequest request, AgentToolExecutor toolExecutor,
            List<Tool> coderSkills);

    /**
     * Executes the agent request and streams events.
     */
    Multi<AgentResponse> stream(AgentRequest request, AgentToolExecutor toolExecutor,
            List<Tool> coderSkills);

    /**
     * Get the system prompt fragment specific to this strategy.
     */
    String getSystemPromptFragment();
}
