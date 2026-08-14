package tech.kayys.aljabr.orchestration;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.wayang.agent.AgentRequest;
import tech.kayys.wayang.agent.AgentResponse;
import tech.kayys.wayang.tool.Tool;
import tech.kayys.wayang.execution.AgentToolExecutor;

import tech.kayys.aljabr.engine.loop.CodingIntent;
import tech.kayys.aljabr.engine.loop.CodingLoop;
import tech.kayys.aljabr.engine.loop.CodingPolicy;
import tech.kayys.aljabr.engine.loop.CodingScope;
import tech.kayys.aljabr.engine.loop.CodingTask;
import tech.kayys.wayang.extension.Id;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class CoderOrchestrator {

    public String strategyId() {
        return "coder";
    }

    @jakarta.inject.Inject
    AgentToolExecutor toolExecutor;

    @jakarta.inject.Inject
    CodingLoop codingLoop;

    @jakarta.inject.Inject
    jakarta.enterprise.inject.Instance<Tool> availableTools;

    @jakarta.inject.Inject
    jakarta.enterprise.inject.Instance<tech.kayys.aljabr.orchestration.strategy.ExecutionStrategy> strategies;

    public Uni<AgentResponse> execute(AgentRequest request) {
        CodingIntent intent = mapIntent(request.content());
        
        CodingTask task = new CodingTask(
                Id.random().asString(),
                request.sessionId(),
                Paths.get(System.getProperty("user.dir")),
                request.content(),
                intent,
                CodingScope.empty(),
                CodingPolicy.defaultPolicy(),
                Id.random().asString(),
                1,
                tech.kayys.aljabr.engine.loop.TaskDeliveryState.PENDING,
                null,
                tech.kayys.aljabr.engine.loop.OrchestrationStrategy.REACT
        );

        List<Tool> tools = availableTools.stream().collect(Collectors.toList());
        return codingLoop.execute(task, toolExecutor, tools);
    }

    private CodingIntent mapIntent(String content) {
        if (content == null || content.isEmpty()) return CodingIntent.EXPLORE;
        String lower = content.toLowerCase();
        if (lower.contains("fix") || lower.contains("bug") || lower.contains("error")) {
            return CodingIntent.FIX;
        } else if (lower.contains("refactor") || lower.contains("clean up")) {
            return CodingIntent.REFACTOR;
        } else if (lower.contains("explain") || lower.contains("how does") || lower.contains("what is")) {
            return CodingIntent.EXPLAIN;
        } else if (lower.contains("test") || lower.contains("unit test")) {
            return CodingIntent.TEST;
        } else if (lower.contains("implement") || lower.contains("create") || lower.contains("add")) {
            return CodingIntent.IMPLEMENT;
        }
        return CodingIntent.EXPLORE;
    }

    public Multi<AgentResponse> stream(AgentRequest request) {
        String strategyId = (String) request.parameters().getOrDefault("strategy", "react");
        tech.kayys.aljabr.orchestration.strategy.ExecutionStrategy strategy = getStrategy(strategyId);

        List<Tool> tools = availableTools.stream().collect(Collectors.toList());
        return strategy.stream(request, toolExecutor, tools);
    }

    private tech.kayys.aljabr.orchestration.strategy.ExecutionStrategy getStrategy(String id) {
        for (tech.kayys.aljabr.orchestration.strategy.ExecutionStrategy s : strategies) {
            if (s.id().equals(id)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown execution strategy: " + id);
    }

    public String getSystemPromptFragment() {
        return "You are an advanced autonomous coding agent. You must query the Language Server (LSP), plan a precise file patch, apply the patch, and run the test suite in the secure terminal until it passes.";
    }

    public boolean supportsToolCalling() {
        return true;
    }

    public List<String> supportedFeatures() {
        return List.of("autonomous-loop", "lsp-integration", "docker-sandbox", "diff-patching",
                "configurable-strategies");
    }
}
