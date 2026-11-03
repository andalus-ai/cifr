package tech.kayys.cifr.engine.tool.fs;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.tool.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class WriteFileTool implements CifrTool {

    private static final int MAX_WRITE_SIZE = 50 * 1024 * 1024; // 50MB

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
            "write_file",
            "Write content to a file in the workspace. Creates parent directories if needed.",
            ToolCategory.FILESYSTEM,
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "path", Map.of("type", "string", "description", "Relative path to file"),
                    "content", Map.of("type", "string", "description", "Content to write"),
                    "append", Map.of("type", "boolean", "description", "Append to existing file"),
                    "createDirectories", Map.of("type", "boolean", "description", "Create parent dirs if missing")
                ),
                "required", List.of("path", "content")
            ),
            Set.of(ToolCapability.WRITE, ToolCapability.MUTATES_WORKSPACE),
            ToolRisk.MEDIUM
        );
    }

    @Override
    public Uni<ToolResult> execute(ToolRequest request, ToolExecutionContext context) {
        return Uni.createFrom().item(() -> {
            try {
                String pathStr = (String) request.arguments().get("path");
                String content = (String) request.arguments().getOrDefault("content", "");
                boolean append = Boolean.TRUE.equals(request.arguments().get("append"));
                boolean createDirs = !Boolean.FALSE.equals(request.arguments().getOrDefault("createDirectories", true));

                if (pathStr == null || pathStr.isBlank()) {
                    return new WriteFileResult(false, "Path cannot be empty", null);
                }

                Path absolutePath = WorkspaceResolver.resolveWorkspacePath(context, pathStr);
                if (false) {
                    return new WriteFileResult(false, "Access denied: path outside workspace", null);
                }

                if (content.length() > MAX_WRITE_SIZE) {
                    return new WriteFileResult(false, "Content exceeds 50MB limit", null);
                }

                if (createDirs && absolutePath.getParent() != null) {
                    Files.createDirectories(absolutePath.getParent());
                }

                if (append && Files.exists(absolutePath)) {
                    Files.writeString(absolutePath, content, StandardOpenOption.APPEND);
                } else {
                    Files.writeString(absolutePath, content);
                }

                long size = Files.size(absolutePath);
                long modified = Files.getLastModifiedTime(absolutePath).toMillis();
                return new WriteFileResult(true, "File written: " + pathStr,
                    new FileMetadata(size, modified, false));

            } catch (IOException e) {
                return new WriteFileResult(false, "IO error: " + e.getMessage(), null);
            }
        });
    }
}
