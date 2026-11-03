package tech.kayys.cifr.orchestration;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.andalus.agent.AgentRequest;
import tech.kayys.andalus.agent.AgentResponse;
import tech.kayys.andalus.tool.Tool;
import tech.kayys.andalus.execution.AgentToolExecutor;

import tech.kayys.cifr.engine.loop.CodingIntent;
import tech.kayys.cifr.engine.loop.CodingLoop;
import tech.kayys.cifr.engine.loop.CodingPolicy;
import tech.kayys.cifr.engine.loop.CodingScope;
import tech.kayys.cifr.engine.loop.CodingTask;
import tech.kayys.andalus.extension.Id;

import java.nio.file.Path;
import java.nio.file.Files;
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
    jakarta.enterprise.inject.Instance<tech.kayys.cifr.engine.tool.CifrTool> cifrTools;

    @jakarta.inject.Inject
    jakarta.enterprise.inject.Instance<tech.kayys.cifr.orchestration.strategy.ExecutionStrategy> strategies;

    public List<Tool> getResolvedTools(String workspacePath) {
        List<Tool> list = new java.util.ArrayList<>();
        if (availableTools != null) {
            availableTools.forEach(list::add);
        }
        if (cifrTools != null) {
            for (tech.kayys.cifr.engine.tool.CifrTool at : cifrTools) {
                list.add(new tech.kayys.cifr.engine.CifrToolAdapter(at, workspacePath));
            }
        }
        return list;
    }

    public Uni<AgentResponse> execute(AgentRequest request) {
        CodingIntent intent = mapIntent(request.content());
        String workspacePath = resolveWorkspacePath(request);
        
        CodingTask task = new CodingTask(
                Id.random().asString(),
                request.sessionId(),
                Paths.get(workspacePath),
                request.content(),
                intent,
                CodingScope.empty(),
                CodingPolicy.defaultPolicy(),
                Id.random().asString(),
                1,
                tech.kayys.cifr.engine.loop.TaskDeliveryState.PENDING,
                null,
                tech.kayys.cifr.engine.loop.OrchestrationStrategy.REACT
        );

        List<Tool> tools = getResolvedTools(workspacePath);
        return codingLoop.execute(task, toolExecutor, tools);
    }

    private String resolveWorkspacePath(AgentRequest request) {
        Object requested = request.parameters() != null ? request.parameters().get("workspace_path") : null;
        String configured = requested instanceof String value && !value.isBlank()
                ? value
                : System.getProperty("cifr.workspace.root");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("CIFR_WORKSPACE_ROOT");
        }
        if (configured == null || configured.isBlank()) {
            throw new IllegalArgumentException(
                    "workspace_path is required (or configure cifr.workspace.root / CIFR_WORKSPACE_ROOT)");
        }
        Path root = Paths.get(configured).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Workspace root is not an existing directory: " + root);
        }
        return root.toString();
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
        String workspacePath = resolveWorkspacePath(request);
        tech.kayys.cifr.orchestration.strategy.ExecutionStrategy strategy = getStrategy(strategyId);

        List<Tool> tools = getResolvedTools(workspacePath);
        return strategy.stream(request, toolExecutor, tools);
    }

    private tech.kayys.cifr.orchestration.strategy.ExecutionStrategy getStrategy(String id) {
        for (tech.kayys.cifr.orchestration.strategy.ExecutionStrategy s : strategies) {
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
