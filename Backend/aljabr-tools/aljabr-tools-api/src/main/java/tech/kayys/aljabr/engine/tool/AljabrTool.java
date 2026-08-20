package tech.kayys.aljabr.engine.tool;

import io.smallrye.mutiny.Uni;

public interface AljabrTool {

    ToolDescriptor descriptor();

    Uni<ToolResult> execute(ToolRequest request, ToolExecutionContext context);
}
