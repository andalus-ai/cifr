package tech.kayys.aljabr.skill;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.muqabalah.api.CodeTool;
import tech.kayys.muqabalah.language.LanguageTypes.HoverResult;
import tech.kayys.wayang.tool.Tool;
import tech.kayys.wayang.tool.ToolDescriptor;
import tech.kayys.wayang.tool.ToolInvocation;
import tech.kayys.wayang.tool.ToolResult;
import tech.kayys.wayang.tool.ToolContext;
import java.util.concurrent.CompletableFuture;

import java.nio.file.Path;
import java.util.Map;

@ApplicationScoped
public class LspHoverSkill extends AljabrTool {

    @Override
    public ToolDescriptor descriptor() {
        return new AljabrToolDescriptor("language", "Target language (java, python, typescript)");
    }


    private static final Logger LOG = Logger.getLogger(LspHoverSkill.class);

    @Inject
    CodeTool codeTool;

    

    

    

    

    

    @Override
    public CompletableFuture<ToolResult> execute(ToolInvocation invocation, tech.kayys.wayang.tool.ToolContext context) {
        String workspacePath = (String) invocation.arguments().get("workspace_path");
        String filePath = (String) invocation.arguments().get("file_path");
        int line = ((Number) invocation.arguments().get("line")).intValue();
        int character = ((Number) invocation.arguments().get("character")).intValue();

        // Ensure the workspace path is used as the workspaceId in muqabalah CodeTool
        String workspaceId = workspacePath != null ? workspacePath : "default";

        return codeTool.hover(workspaceId, Path.of(filePath), line, character)
                .map(hoverResult -> {
                    if (hoverResult.content() != null && !hoverResult.content().isEmpty()) {
                        return (ToolResult) AljabrToolResult.success(hoverResult.content());
                    }
                    return (ToolResult) AljabrToolResult.success("No hover information found at this position.");
                })
                .onFailure().recoverWithItem(e -> {
                    LOG.error("LSP Hover Query failed", e);
                    return (ToolResult) AljabrToolResult.error(e.getMessage());
                }).subscribeAsCompletionStage();
    }

    
}
