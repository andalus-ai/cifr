package tech.kayys.aljabr.engine.tool.index;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.aljabr.engine.index.RepositoryIntelligence;
import tech.kayys.aljabr.engine.index.relation.RelatedFileRequest;
import tech.kayys.aljabr.engine.tool.*;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class FindRelatedFilesTool implements AljabrTool {

    @Inject
    RepositoryIntelligence intelligence;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
            "find_related_files",
            "Find files related to a specific file in the workspace (e.g., test files, implementations).",
            ToolCategory.SEARCH,
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "path", Map.of("type", "string", "description", "The path of the file to find relations for")
                ),
                "required", java.util.List.of("path")
            ),
            Set.of(ToolCapability.READ_ONLY),
            ToolRisk.NONE
        );
    }

    @Override
    public Uni<ToolResult> execute(ToolRequest request, ToolExecutionContext context) {
        String pathStr = (String) request.arguments().get("path");
        
        RelatedFileRequest relReq = new RelatedFileRequest(Path.of(pathStr), 10);
        
        return intelligence.related(relReq).map(relations -> {
            String resultText = relations.stream()
                .map(r -> r.path().toString() + " (Score: " + r.score() + ")")
                .collect(Collectors.joining("\n"));
                
            return new SimpleToolResult(
                true,
                resultText.isEmpty() ? "No related files found." : resultText
            );
        });
    }
}
