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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class DapInspectionSkill extends AljabrTool {

    @Override
    public ToolDescriptor descriptor() {
        return new AljabrToolDescriptor("DAP Inspection Skill", "Inspects the debuggee's state (stackTrace, variables, evaluate).");
    }


    private static final Logger LOG = Logger.getLogger(DapInspectionSkill.class);

    @Inject
    DebugTool debugTool;

    

    

    

    

    

    @Override
    public CompletableFuture<ToolResult> execute(ToolInvocation invocation, tech.kayys.wayang.tool.ToolContext context) {
        String sessionId = (String) invocation.arguments().get("session_id");
        String action = (String) invocation.arguments().get("action");

        try {
            if ("stacktrace".equalsIgnoreCase(action)) {
                int threadId = getInt(invocation, "thread_id");
                return debugTool.stackTrace(sessionId, threadId)
                        .map(stackTrace -> {
                            List<Map<String, Object>> frames = stackTrace.frames().stream().map(f -> {
                                Map<String, Object> map = new LinkedHashMap<>();
                                map.put("id", f.id());
                                map.put("name", f.name());
                                map.put("file", f.file() != null ? f.file().toString() : null);
                                map.put("line", f.line());
                                map.put("column", f.column());
                                return map;
                            }).collect(Collectors.toList());
                            return (ToolResult) AljabrToolResult.successMap(Map.of("status", "success", "result", frames));
                        }).subscribeAsCompletionStage();
            } else if ("variables".equalsIgnoreCase(action)) {
                int varRef = getInt(invocation, "variables_reference");
                return debugTool.variables(sessionId, varRef)
                        .map(vars -> {
                            List<Map<String, Object>> resultVars = vars.stream().map(v -> {
                                Map<String, Object> map = new LinkedHashMap<>();
                                map.put("name", v.name());
                                map.put("value", v.value());
                                map.put("type", v.type());
                                map.put("variablesReference", v.variablesReference());
                                return map;
                            }).collect(Collectors.toList());
                            return (ToolResult) AljabrToolResult.successMap(Map.of("status", "success", "result", resultVars));
                        }).subscribeAsCompletionStage();
            } else if ("evaluate".equalsIgnoreCase(action)) {
                int frameId = getInt(invocation, "frame_id");
                String expression = (String) invocation.arguments().get("expression");
                if (expression == null) {
                    return Uni.createFrom().item((ToolResult) AljabrToolResult.error("expression is required")).subscribeAsCompletionStage();
                }
                return debugTool.evaluate(sessionId, frameId, expression)
                        .map(result -> (ToolResult) AljabrToolResult.successMap(Map.of("status", "success", "result", result))).subscribeAsCompletionStage();
            } else {
                return Uni.createFrom().item((ToolResult) AljabrToolResult.error("Unknown action: " + action)).subscribeAsCompletionStage();
            }
        } catch (Exception e) {
            return Uni.createFrom().item((ToolResult) AljabrToolResult.error(e.getMessage())).subscribeAsCompletionStage();
        }
    }

    private int getInt(ToolInvocation invocation, String key) {
        Object val = invocation.arguments().get(key);
        if (val == null) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        return ((Number) val).intValue();
    }

    
}
