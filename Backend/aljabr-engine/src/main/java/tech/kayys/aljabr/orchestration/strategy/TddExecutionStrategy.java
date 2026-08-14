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
public class TddExecutionStrategy implements ExecutionStrategy {
    @Override
    public String id() { return "tdd"; }

    @Override
    public String getSystemPromptFragment() {
        return "You must operate in a strict Test-Driven Development (TDD) loop. 1) Write a failing test. 2) Run the test. 3) Write code to pass the test. 4) Run the test again to verify.";
    }

    @Override
    public Uni<AgentResponse> execute(AgentRequest request, AgentToolExecutor toolExecutor, List<Tool> coderSkills) {
        return Uni.createFrom().item(AgentResponse.builder().content("Tdd strategy executed").build());
    }

    @Override
    public Multi<AgentResponse> stream(AgentRequest request, AgentToolExecutor toolExecutor, List<Tool> coderSkills) {
        return Multi.createFrom().empty();
    }
}
