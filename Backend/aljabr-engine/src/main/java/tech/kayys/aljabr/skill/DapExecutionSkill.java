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

import java.util.Map;

@ApplicationScoped
public class DapExecutionSkill extends AljabrTool {

    @Override
    public ToolDescriptor descriptor() {
        return new AljabrToolDescriptor("DAP Execution Skill", "Steps through code during a debug session (resume, stepOver, stepIn, stepOut, pause).");
    }


    private static final Logger LOG = Logger.getLogger(DapExecutionSkill.class);

    @Inject
    DebugTool debugTool;

    

    

    

    

    

    @Override
    public CompletableFuture<ToolResult> execute(ToolInvocation invocation, tech.kayys.wayang.tool.ToolContext context) {
        String sessionId = (String) invocation.arguments().get("session_id");
        int threadId = ((Number) invocation.arguments().get("thread_id")).intValue();
        String action = (String) invocation.arguments().get("action");

        Uni<Void> actionUni;
        switch (action.toLowerCase()) {
            case "resume":
                actionUni = debugTool.resume(sessionId, threadId);
                break;
            case "stepover":
                actionUni = debugTool.stepOver(sessionId, threadId);
                break;
            case "stepin":
                actionUni = debugTool.stepIn(sessionId, threadId);
                break;
            case "stepout":
                actionUni = debugTool.stepOut(sessionId, threadId);
                break;
            case "pause":
                actionUni = debugTool.pause(sessionId, threadId);
                break;
            default:
                return Uni.createFrom().item((ToolResult) AljabrToolResult.error("Unknown action: " + action)).subscribeAsCompletionStage();
        }

        return actionUni
                .map(v -> (ToolResult) AljabrToolResult.success("Successfully executed " + action))
                .onFailure().recoverWithItem(e -> {
                    LOG.error("Failed to execute DAP action: " + action, e);
                    return (ToolResult) AljabrToolResult.error(e.getMessage());
                }).subscribeAsCompletionStage();
    }

    
}
