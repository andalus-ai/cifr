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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class DapBreakpointSkill extends AljabrTool {

    @Override
    public ToolDescriptor descriptor() {
        return new AljabrToolDescriptor("session_id", "The ID of the active debug session");
    }


    private static final Logger LOG = Logger.getLogger(DapBreakpointSkill.class);

    @Inject
    DebugTool debugTool;

    

    

    

    

    

    @Override
    public CompletableFuture<ToolResult> execute(ToolInvocation invocation, tech.kayys.wayang.tool.ToolContext context) {
        String sessionId = (String) invocation.arguments().get("session_id");
        String filePath = (String) invocation.arguments().get("file_path");

        List<?> rawLines = (List<?>) invocation.arguments().get("lines");
        List<Integer> lines = rawLines.stream()
                .map(obj -> ((Number) obj).intValue())
                .collect(Collectors.toList());

        return debugTool.setBreakpoints(sessionId, Path.of(filePath), lines)
                .map(v -> (ToolResult) AljabrToolResult.success("Breakpoints set successfully"))
                .onFailure().recoverWithItem(e -> {
                    LOG.error("Failed to set breakpoints", e);
                    return (ToolResult) AljabrToolResult.error(e.getMessage());
                }).subscribeAsCompletionStage();
    }

    
}
