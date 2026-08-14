package tech.kayys.aljabr.skill;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.muqabalah.api.CodeTool;
import tech.kayys.muqabalah.language.LanguageTypes.CodeLocation;
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
import java.util.stream.Collectors;

@ApplicationScoped
public class LspQuerySkill extends AljabrTool {

    @Override
    public ToolDescriptor descriptor() {
        return new AljabrToolDescriptor("language", "Target language (java, python, typescript)");
    }


    private static final Logger LOG = Logger.getLogger(LspQuerySkill.class);

    @Inject
    CodeTool codeTool;

    

    

    

    

    

    @Override
    public CompletableFuture<ToolResult> execute(ToolInvocation invocation, tech.kayys.wayang.tool.ToolContext context) {
        String workspacePath = (String) invocation.arguments().get("workspace_path");
        String filePath = (String) invocation.arguments().get("file_path");
        int line = ((Number) invocation.arguments().get("line")).intValue();
        int character = ((Number) invocation.arguments().get("character")).intValue();
        String queryType = (String) invocation.arguments().get("query_type");

        String workspaceId = workspacePath != null ? workspacePath : "default";

        Uni<List<CodeLocation>> queryResultUni;
        if ("definition".equalsIgnoreCase(queryType)) {
            queryResultUni = codeTool.definition(workspaceId, Path.of(filePath), line, character);
        } else if ("references".equalsIgnoreCase(queryType)) {
            queryResultUni = codeTool.references(workspaceId, Path.of(filePath), line, character);
        } else {
            return Uni.createFrom().item((ToolResult) AljabrToolResult.error("Unsupported query_type: " + queryType)).subscribeAsCompletionStage();
        }

        return queryResultUni
                .map(locations -> {
                    List<Map<String, Object>> resultLocations = locations.stream()
                            .map(this::mapLocation)
                            .collect(Collectors.toList());
                    return (ToolResult) AljabrToolResult.successMap(Map.of("status", "success", "results", resultLocations));
                })
                .onFailure().recoverWithItem(e -> {
                    LOG.error("LSP Query failed", e);
                    return (ToolResult) AljabrToolResult.error(e.getMessage());
                }).subscribeAsCompletionStage();
    }

    private Map<String, Object> mapLocation(CodeLocation loc) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("uri", loc.uri().toString());
        map.put("start_line", loc.range().start().line());
        map.put("start_char", loc.range().start().character());
        map.put("end_line", loc.range().end().line());
        map.put("end_char", loc.range().end().character());
        return map;
    }

    
}
