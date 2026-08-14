package tech.kayys.aljabr.orchestration.strategy;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.wayang.agent.spi.AgentEvent;
import tech.kayys.wayang.agent.AgentRequest;
import tech.kayys.wayang.agent.AgentResponse;
import tech.kayys.wayang.agent.spi.InferenceBackend;
import tech.kayys.wayang.tool.Tool;
import tech.kayys.wayang.execution.AgentToolExecutor;

import java.util.List;

@ApplicationScoped
public class PlanAndSolveExecutionStrategy implements ExecutionStrategy {
    @Override
    public String id() { return "plan_and_solve"; }

    @Override
    public String getSystemPromptFragment() {
        return "You must first output a detailed step-by-step plan (checklist) to solve the problem. Then, execute the plan one step at a time, emitting a TOOL_CALL for each step. After receiving an OBSERVATION, mark the step complete and proceed to the next until finished.";
    }

    @Override
    public Uni<AgentResponse> execute(AgentRequest request, AgentToolExecutor toolExecutor, List<Tool> coderSkills) {
        return Uni.createFrom().item(AgentResponse.builder().content("PlanAndSolve strategy executed").build());
    }

    @Override
    public Multi<AgentResponse> stream(AgentRequest request, AgentToolExecutor toolExecutor, List<Tool> coderSkills) {
        return Multi.createFrom().empty();
    }
}
