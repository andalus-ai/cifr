package tech.kayys.aljabr.engine.tool.index;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.aljabr.engine.index.RepositoryIntelligence;
import tech.kayys.aljabr.engine.index.reference.ReferenceRequest;
import tech.kayys.aljabr.engine.index.symbol.SymbolId;
import tech.kayys.aljabr.engine.tool.*;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class FindReferencesTool implements AljabrTool {

    @Inject
    RepositoryIntelligence intelligence;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
            "find_references",
            "Find all references to a specific symbol in the workspace using the LSP backend.",
            ToolCategory.SEARCH,
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "fqn", Map.of("type", "string", "description", "Fully qualified name of the symbol"),
                    "name", Map.of("type", "string", "description", "Name of the symbol")
                ),
                "required", java.util.List.of("fqn", "name")
            ),
            Set.of(ToolCapability.READ_ONLY),
            ToolRisk.NONE
        );
    }

    @Override
    public Uni<ToolResult> execute(ToolRequest request, ToolExecutionContext context) {
        String fqn = (String) request.arguments().get("fqn");
        String name = (String) request.arguments().get("name");
        
        SymbolId target = new SymbolId(fqn, name, null);
        ReferenceRequest refReq = new ReferenceRequest(target, 100);
        
        return intelligence.references(refReq).map(refs -> {
            String resultText = refs.stream()
                .map(r -> r.sourceFile().toString() + ":" + r.sourceRange().startLine() + " - " + r.kind())
                .collect(Collectors.joining("\n"));
                
            return new SimpleToolResult(
                true,
                resultText.isEmpty() ? "No references found." : resultText
            );
        });
    }
}
