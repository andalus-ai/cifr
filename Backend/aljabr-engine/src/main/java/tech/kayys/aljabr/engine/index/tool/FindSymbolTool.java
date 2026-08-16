package tech.kayys.aljabr.engine.index.tool;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.aljabr.engine.index.RepositoryIntelligence;
import tech.kayys.aljabr.engine.index.symbol.SymbolId;
import tech.kayys.aljabr.engine.tool.*;

import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class FindSymbolTool implements AljabrTool {

    @Inject
    RepositoryIntelligence intelligence;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
            "find_symbol",
            "Find information about a specific symbol (class, method, variable) in the workspace.",
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
        
        String signature = (String) request.arguments().get("name"); // mapping name to signature for now as simple fallback
        SymbolId target = new SymbolId(fqn, signature, null);
        
        return intelligence.symbol(target).map(info -> {
            if (info == null) {
                return new SimpleToolResult(true, "Symbol not found.");
            }
            String resultText = "Symbol: " + info.id().qualifiedName() + "\n" +
                                "Kind: " + info.kind() + "\n" +
                                "File: " + (info.file() != null ? info.file().toString() : "unknown") + "\n" +
                                "Signature: " + info.id().signature();
                                
            return new SimpleToolResult(true, resultText);
        });
    }
}
