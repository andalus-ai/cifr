package tech.kayys.cifr.engine.tool.fs;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.tool.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class ReadFileTool implements CifrTool {

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
            "read_file",
            "Read source content from a file in the workspace with optional line range slicing.",
            ToolCategory.FILESYSTEM,
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "path", Map.of("type", "string", "description", "Relative path to file"),
                    "startLine", Map.of("type", "integer", "description", "Starting line (1-indexed)"),
                    "endLine", Map.of("type", "integer", "description", "Ending line (inclusive)")
                ),
                "required", List.of("path")
            ),
            Set.of(ToolCapability.READ_ONLY),
            ToolRisk.NONE
        );
    }

    @Override
    public Uni<ToolResult> execute(ToolRequest request, ToolExecutionContext context) {
        return Uni.createFrom().item(() -> {
            try {
                String pathStr = (String) request.arguments().get("path");
                if (pathStr == null || pathStr.isBlank()) {
                    return new ReadFileResult("", 0, 0, List.of(), new FileMetadata(0L, 0L, true));
                }

                Path workspaceRoot = WorkspaceResolver.resolveWorkspaceRoot(context);
                Path absPath = WorkspaceResolver.resolveWorkspacePath(context, pathStr);
                if (!Files.exists(absPath) || Files.isDirectory(absPath)) {
                    return new ReadFileResult(pathStr, 0, 0, List.of(), new FileMetadata(0L, 0L, true));
                }

                int startLine = ((Number) request.arguments().getOrDefault("startLine", 1)).intValue();
                int endLine = ((Number) request.arguments().getOrDefault("endLine", Integer.MAX_VALUE)).intValue();

                List<String> allLines = Files.readAllLines(absPath);
                int totalLines = allLines.size();

                int actualStart = Math.max(1, Math.min(startLine, totalLines));
                int actualEnd = Math.max(actualStart, Math.min(endLine, totalLines));

                List<SourceLine> sourceLines = new ArrayList<>();
                for (int i = actualStart; i <= actualEnd && i <= totalLines; i++) {
                    sourceLines.add(new SourceLine(i, allLines.get(i - 1)));
                }

                long sizeBytes = Files.size(absPath);
                long lastModified = Files.getLastModifiedTime(absPath).toMillis();
                boolean isReadOnly = !Files.isWritable(absPath);

                return new ReadFileResult(
                    pathStr,
                    actualStart,
                    actualEnd,
                    sourceLines,
                    new FileMetadata(sizeBytes, lastModified, isReadOnly)
                );
            } catch (IOException e) {
                return new ReadFileResult(
                    String.valueOf(request.arguments().get("path")),
                    0,
                    0,
                    List.of(),
                    new FileMetadata(0L, 0L, true)
                );
            }
        });
    }
}
