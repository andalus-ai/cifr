package tech.kayys.aljabr.engine.tool.policy;
import tech.kayys.aljabr.engine.tool.middleware.ToolContext;
import tech.kayys.aljabr.engine.tool.protocol.ToolDescriptor;
import tech.kayys.aljabr.engine.tool.request.ToolRequest;
public interface ToolOutputPolicy {
    ToolOutputPlan plan(ToolDescriptor descriptor, ToolRequest request, long contextBudgetRemaining);
}
