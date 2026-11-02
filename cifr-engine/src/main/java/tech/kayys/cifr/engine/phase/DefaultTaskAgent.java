package tech.kayys.cifr.engine.phase;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.gate.CompletionClaim;
import tech.kayys.cifr.engine.loop.CodingState;
import tech.kayys.cifr.engine.loop.CodingTask;
import tech.kayys.cifr.engine.memory.CifrMemoryService;
import tech.kayys.cifr.engine.memory.MemoryFact;
import tech.kayys.cifr.engine.memory.MemoryLayer;
import tech.kayys.cifr.orchestration.strategy.ExecutionStrategy;
import tech.kayys.andalus.agent.AgentRequest;
import tech.kayys.andalus.execution.AgentToolExecutor;
import tech.kayys.andalus.tool.Tool;

import java.util.List;
import java.util.logging.Logger;

@ApplicationScoped
public class DefaultTaskAgent implements TaskAgent {
    private static final Logger log = Logger.getLogger(DefaultTaskAgent.class.getName());

    @jakarta.inject.Inject
    CifrMemoryService memoryService;

    @jakarta.inject.Inject
    jakarta.enterprise.inject.Instance<ExecutionStrategy> strategies;

    @Override
    public Uni<TaskExecutionResult> execute(CodingTask task, TaskExecutionContext context) {
        log.info("DefaultTaskAgent: Executing task " + task.intent());

        CodingState state = context.state();
        StringBuilder prompt = new StringBuilder();
        prompt.append("Task: ").append(state.getTask().intent()).append("\n");
        prompt.append("Request: ").append(state.getTask().request()).append("\n\n");
        
        if (state.getPlan() != null) {
            prompt.append("Plan:\n");
            for (String step : state.getPlan().steps()) {
                prompt.append("- ").append(step).append("\n");
            }
        }
        
        if (state.getIteration() > 1 && state.getVerification() != null && !state.getVerification().passed()) {
            prompt.append("\nPREVIOUS VERIFICATION FAILED. Please fix the following errors:\n");
            for (String diag : state.getVerification().diagnostics()) {
                prompt.append(diag).append("\n");
            }
        }
        
        List<MemoryFact> projectFacts = memoryService.getFactsForLayer(MemoryLayer.PROJECT);
        if (!projectFacts.isEmpty()) {
            prompt.append("\nPROJECT CONTEXT (Rules and Patterns):\n");
            for (MemoryFact fact : projectFacts) {
                prompt.append("- ").append(fact.content()).append("\n");
            }
        }

        AgentRequest request = AgentRequest.builder()
                .sessionId(state.getTask().sessionId())
                .content(prompt.toString())
                .build();

        ExecutionStrategy strategy = getStrategy("react");
        
        return strategy.execute(request, context.executor(), context.tools())
                .map(response -> new TaskExecutionResult(
                    "COMPLETED",
                    response.content(),
                    List.of(),
                    CompletionClaim.CHANGE_APPLIED
                ));
    }

    private ExecutionStrategy getStrategy(String id) {
        return strategies.stream()
                .filter(s -> s.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown strategy: " + id));
    }
}
