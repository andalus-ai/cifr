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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class LspWorkspaceSymbolSkill extends AljabrTool {

    @Override
    public ToolDescriptor descriptor() {
        return new AljabrToolDescriptor("workspace_path", "Absolute path to workspace root");
    }


    private static final Logger LOG = Logger.getLogger(LspWorkspaceSymbolSkill.class);

    @Inject
    CodeTool codeTool;

    

    

    

    

    

    @Override
    public CompletableFuture<ToolResult> execute(ToolInvocation invocation, tech.kayys.wayang.tool.ToolContext context) {
        String workspacePath = (String) invocation.arguments().get("workspace_path");
        String query = (String) invocation.arguments().get("query");
        String workspaceId = workspacePath != null ? workspacePath : "default";

        return codeTool.workspaceSymbols(workspaceId, query)
                .map(symbols -> {
                    List<Map<String, Object>> mappedSymbols = symbols.stream()
                            .map(this::mapSymbol)
                            .collect(Collectors.toList());
                    return (ToolResult) AljabrToolResult.successMap(Map.of("status", "success", "symbols", mappedSymbols));
                })
                .onFailure().recoverWithItem(e -> {
                    LOG.error("Workspace symbol search failed", e);
                    return (ToolResult) AljabrToolResult.error(e.getMessage());
                }).subscribeAsCompletionStage();
    }

    private Map<String, Object> mapSymbol(SymbolEntry symbol) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", symbol.name());
        map.put("kind", symbol.kind());
        map.put("uri", symbol.uri().toString());
        map.put("start_line", symbol.range().start().line());
        map.put("start_char", symbol.range().start().character());
        map.put("end_line", symbol.range().end().line());
        map.put("end_char", symbol.range().end().character());
        return map;
    }

    
}
