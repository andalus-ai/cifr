package tech.kayys.cifr.engine.tool.policy;
import tech.kayys.cifr.engine.tool.middleware.ToolContext;
import tech.kayys.cifr.engine.tool.protocol.ToolDescriptor;
import tech.kayys.cifr.engine.tool.request.ToolRequest;
public interface ToolOutputPolicy {
    ToolOutputPlan plan(ToolDescriptor descriptor, ToolRequest request, long contextBudgetRemaining);
}
