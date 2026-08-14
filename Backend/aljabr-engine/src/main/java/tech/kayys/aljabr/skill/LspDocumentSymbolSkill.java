package tech.kayys.aljabr.skill;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.muqabalah.api.CodeTool;
import tech.kayys.muqabalah.language.LanguageTypes.SymbolEntry;
import tech.kayys.wayang.tool.Tool;
import tech.kayys.wayang.tool.ToolDescriptor;
import tech.kayys.wayang.tool.ToolInvocation;
import tech.kayys.wayang.tool.ToolResult;
import tech.kayys.wayang.tool.ToolContext;
import java.util.concurrent.CompletableFuture;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class LspDocumentSymbolSkill extends AljabrTool {

    @Override
    public ToolDescriptor descriptor() {
        return new AljabrToolDescriptor("LSP Document Symbol Skill", "Retrieves an outline of all symbols (classes, methods, fields) in a file.");
    }


    private static final Logger LOG = Logger.getLogger(LspDocumentSymbolSkill.class);

    @Inject
    CodeTool codeTool;

    

    

    

    

    

    @Override
    public CompletableFuture<ToolResult> execute(ToolInvocation invocation, tech.kayys.wayang.tool.ToolContext context) {
        String workspacePath = (String) invocation.arguments().get("workspace_path");
        String filePath = (String) invocation.arguments().get("file_path");

        String workspaceId = workspacePath != null ? workspacePath : "default";

        return codeTool.documentSymbols(workspaceId, Path.of(filePath))
                .map(symbols -> {
                    List<Map<String, Object>> parsedSymbols = new ArrayList<>();
                    if (symbols != null) {
                        for (SymbolEntry symbol : symbols) {
                            parsedSymbols.add(mapSymbolEntry(symbol));
                        }
                    }
                    return (ToolResult) AljabrToolResult.successMap(Map.of("status", "success", "symbols", parsedSymbols));
                })
                .onFailure().recoverWithItem(e -> {
                    LOG.error("LSP Document Symbol Query failed", e);
                    return (ToolResult) AljabrToolResult.error(e.getMessage());
                }).subscribeAsCompletionStage();
    }

    private Map<String, Object> mapSymbolEntry(SymbolEntry symbol) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", symbol.name());
        map.put("kind", symbol.kind() != null ? symbol.kind() : "Unknown");
        map.put("line", symbol.range().start().line());

        if (symbol.containerName() != null && !symbol.containerName().isEmpty()) {
            map.put("container", symbol.containerName());
        }

        if (symbol.children() != null && !symbol.children().isEmpty()) {
            List<Map<String, Object>> children = new ArrayList<>();
            for (SymbolEntry child : symbol.children()) {
                children.add(mapSymbolEntry(child));
            }
            map.put("children", children);
        }
        return map;
    }

    
}
