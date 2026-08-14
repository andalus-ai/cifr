package tech.kayys.aljabr.skill;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.muqabalah.api.DebugTool;
import tech.kayys.wayang.tool.Tool;
import tech.kayys.wayang.tool.ToolDescriptor;
import tech.kayys.wayang.tool.ToolInvocation;
import tech.kayys.wayang.tool.ToolResult;
import tech.kayys.wayang.tool.ToolContext;
import java.util.concurrent.CompletableFuture;

import java.nio.file.Path;
import java.util.Map;

@ApplicationScoped
public class DapSessionSkill extends AljabrTool {

    @Override
    public ToolDescriptor descriptor() {
        return new AljabrToolDescriptor("DAP Session Skill", "Launches or terminates a Debug Adapter Protocol (DAP) session for debugging.");
    }


    private static final Logger LOG = Logger.getLogger(DapSessionSkill.class);

    @Inject
    DebugTool debugTool;

    

    

    

    

    

    @Override
    public CompletableFuture<ToolResult> execute(ToolInvocation invocation, tech.kayys.wayang.tool.ToolContext context) {
        String action = (String) invocation.arguments().get("action");
        String workspacePath = (String) invocation.arguments().get("workspace_path");
        String workspaceId = workspacePath != null ? workspacePath : "default";

        if ("launch".equalsIgnoreCase(action)) {
            String program = (String) invocation.arguments().get("program");
            if (program == null) {
                return Uni.createFrom().item((ToolResult) AljabrToolResult.error("program path required for launch")).subscribeAsCompletionStage();
            }
            return debugTool.launch(workspaceId, Path.of(program), Map.of())
                    .map(sessionId -> (ToolResult) AljabrToolResult.successMap(Map.of(
                            "status", "success",
                            "session_id", sessionId,
                            "message", "Debug session launched successfully")))
                    .onFailure().recoverWithItem(e -> {
                        LOG.error("Failed to launch debug session", e);
                        return (ToolResult) AljabrToolResult.error(e.getMessage());
                    }).subscribeAsCompletionStage();
        } else if ("terminate".equalsIgnoreCase(action)) {
            String sessionId = (String) invocation.arguments().get("session_id");
            if (sessionId == null) {
                return Uni.createFrom().item((ToolResult) AljabrToolResult.error("session_id required for terminate")).subscribeAsCompletionStage();
            }
            return debugTool.terminate(sessionId)
                    .map(v -> (ToolResult) AljabrToolResult.success("Debug session terminated"))
                    .onFailure().recoverWithItem(e -> {
                        LOG.error("Failed to terminate debug session", e);
                        return (ToolResult) AljabrToolResult.error(e.getMessage());
                    }).subscribeAsCompletionStage();
        }

        return Uni.createFrom().item((ToolResult) AljabrToolResult.error("Unknown action: " + action)).subscribeAsCompletionStage();
    }

    
}
