package tech.kayys.aljabr.engine.tool.index;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.aljabr.engine.index.RepositoryIntelligence;
import tech.kayys.aljabr.engine.index.dependency.DependencyKind;
import tech.kayys.aljabr.engine.index.dependency.DependencyRequest;
import tech.kayys.aljabr.engine.tool.*;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class FindDependenciesTool implements AljabrTool {

    @Inject
    RepositoryIntelligence intelligence;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
            "find_dependencies",
            "Find dependencies for a given module or project in the workspace.",
            ToolCategory.SEARCH,
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "target", Map.of("type", "string", "description", "The target module or class")
                ),
                "required", java.util.List.of("target")
            ),
            Set.of(ToolCapability.READ_ONLY),
            ToolRisk.NONE
        );
    }

    @Override
    public Uni<ToolResult> execute(ToolRequest request, ToolExecutionContext context) {
        String target = (String) request.arguments().get("target");
        
        DependencyRequest depReq = new DependencyRequest(target, DependencyKind.MODULE);
        
        return intelligence.dependencies(depReq).map(deps -> {
            String resultText = deps.stream()
                .map(d -> d.target() + " (" + d.kind() + ")")
                .collect(Collectors.joining("\n"));
                
            return new SimpleToolResult(
                true,
                resultText.isEmpty() ? "No dependencies found." : resultText
            );
        });
    }
}
