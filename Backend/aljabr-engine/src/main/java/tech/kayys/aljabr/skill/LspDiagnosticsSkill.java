package tech.kayys.aljabr.skill;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.muqabalah.api.CodeTool;
import tech.kayys.wayang.tool.Tool;
import tech.kayys.wayang.tool.ToolDescriptor;
import tech.kayys.wayang.tool.ToolInvocation;
import tech.kayys.wayang.tool.ToolResult;
import tech.kayys.wayang.tool.ToolContext;
import java.util.concurrent.CompletableFuture;

import java.nio.file.Path;
import java.util.Map;

@ApplicationScoped
public class LspDiagnosticsSkill extends AljabrTool {

    @Override
    public ToolDescriptor descriptor() {
        return new AljabrToolDescriptor("workspace_path", "Absolute path to workspace root");
    }


    private static final Logger LOG = Logger.getLogger(LspDiagnosticsSkill.class);

    @Inject
    CodeTool codeTool;

    

    

    

    

    

    @Override
    public CompletableFuture<ToolResult> execute(ToolInvocation invocation, tech.kayys.wayang.tool.ToolContext context) {
        String workspacePath = (String) invocation.arguments().get("workspace_path");
        String filePath = (String) invocation.arguments().get("file_path");
        String workspaceId = workspacePath != null ? workspacePath : "default";

        return codeTool.fixDiagnostics(workspaceId, Path.of(filePath))
                .map(v -> (ToolResult) AljabrToolResult.success("Applied quickfixes for " + filePath))
                .onFailure().recoverWithItem(e -> {
                    LOG.error("Failed to fix diagnostics", e);
                    return (ToolResult) AljabrToolResult.error(e.getMessage());
                }).subscribeAsCompletionStage();
    }

    
}
