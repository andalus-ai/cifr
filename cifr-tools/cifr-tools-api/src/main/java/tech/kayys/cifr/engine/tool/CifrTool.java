package tech.kayys.cifr.engine.tool;

import io.smallrye.mutiny.Uni;

public interface CifrTool {

    ToolDescriptor descriptor();

    Uni<ToolResult> execute(ToolRequest request, ToolExecutionContext context);
}
