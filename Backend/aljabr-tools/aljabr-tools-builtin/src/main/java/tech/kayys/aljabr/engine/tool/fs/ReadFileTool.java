package tech.kayys.aljabr.engine.tool.fs;

import io.smallrye.mutiny.Uni;
import java.util.Collections;
import java.util.Set;
import tech.kayys.aljabr.engine.tool.AljabrTool;
import tech.kayys.aljabr.engine.tool.ToolCapability;
import tech.kayys.aljabr.engine.tool.ToolCategory;
import tech.kayys.aljabr.engine.tool.ToolDescriptor;
import tech.kayys.aljabr.engine.tool.ToolExecutionContext;
import tech.kayys.aljabr.engine.tool.ToolRequest;
import tech.kayys.aljabr.engine.tool.ToolResult;
import tech.kayys.aljabr.engine.tool.ToolRisk;

public class ReadFileTool implements AljabrTool {

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
            "read_file",
            "Read source content from the workspace.",
            ToolCategory.FILESYSTEM,
            Collections.emptyMap(), // Stub schema
            Set.of(ToolCapability.READ_ONLY),
            ToolRisk.NONE
        );
    }

    @Override
    public Uni<ToolResult> execute(ToolRequest request, ToolExecutionContext context) {
        // Stub implementation
        return Uni.createFrom().item(new ReadFileResult(
            String.valueOf(request.arguments().get("path")),
            1,
            1,
            Collections.emptyList(),
            new FileMetadata(0L, 0L, true)
        ));
    }
}
