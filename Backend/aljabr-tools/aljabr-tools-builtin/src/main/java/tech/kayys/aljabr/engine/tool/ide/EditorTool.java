package tech.kayys.aljabr.engine.tool.ide;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.tool.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@ApplicationScoped
public class EditorTool implements AljabrTool {

    private static final Path WORKSPACE_ROOT = Paths.get(System.getProperty("user.dir"));

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
            "editor",
            "In-memory and file editor for reading, editing line slices, and writing files.",
            ToolCategory.CODE_ANALYSIS,
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "action", Map.of(
                        "type", "string",
                        "enum", List.of("open", "edit", "save"),
                        "description", "Editor action"
                    ),
                    "path", Map.of("type", "string", "description", "File path"),
                    "startLine", Map.of("type", "integer", "description", "Start line (1-indexed)"),
                    "endLine", Map.of("type", "integer", "description", "End line (1-indexed)"),
                    "content", Map.of("type", "string", "description", "Replacement content")
                ),
                "required", List.of("action", "path")
            ),
            Set.of(ToolCapability.READ_ONLY, ToolCapability.WRITE),
            ToolRisk.MEDIUM
        );
    }

    @Override
    public Uni<ToolResult> execute(ToolRequest request, ToolExecutionContext context) {
        return Uni.createFrom().item(() -> {
            try {
                String action = (String) request.arguments().get("action");
                String path = (String) request.arguments().get("path");

                if (path == null || path.isBlank()) {
                    return new EditorResult(false, "path is required", null);
                }

                Path resolvedPath = WORKSPACE_ROOT.resolve(path).normalize();
                if (!resolvedPath.startsWith(WORKSPACE_ROOT)) {
                    return new EditorResult(false, "Access denied: path outside workspace", null);
                }

                return switch (action.toLowerCase()) {
                    case "open" -> openFile(resolvedPath);
                    case "edit" -> editFile(resolvedPath, request);
                    case "save" -> saveFile(resolvedPath, request);
                    default -> new EditorResult(false, "Unknown action: " + action, null);
                };

            } catch (Exception e) {
                return new EditorResult(false, "Editor operation failed: " + e.getMessage(), null);
            }
        });
    }

    private EditorResult openFile(Path path) {
        try {
            if (!Files.exists(path)) return new EditorResult(false, "File not found: " + path, null);
            String content = Files.readString(path);
            return new EditorResult(true, "File opened", content);
        } catch (IOException e) {
            return new EditorResult(false, "Failed to open: " + e.getMessage(), null);
        }
    }

    private EditorResult editFile(Path path, ToolRequest request) {
        try {
            if (!Files.exists(path)) return new EditorResult(false, "File not found: " + path, null);
            String content = (String) request.arguments().get("content");
            if (content == null) return new EditorResult(false, "content is required for edit", null);

            Integer startLine = (Integer) request.arguments().get("startLine");
            Integer endLine = (Integer) request.arguments().get("endLine");

            if (startLine != null && endLine != null) {
                List<String> lines = Files.readAllLines(path);
                int start = Math.max(1, startLine) - 1;
                int end = Math.min(lines.size(), endLine);

                List<String> newLines = new ArrayList<>();
                for (int i = 0; i < start; i++) newLines.add(lines.get(i));
                newLines.add(content);
                for (int i = end; i < lines.size(); i++) newLines.add(lines.get(i));

                String updated = String.join("\n", newLines);
                Files.writeString(path, updated);
                return new EditorResult(true, "Edited line range " + startLine + "-" + endLine, updated);
            } else {
                Files.writeString(path, content);
                return new EditorResult(true, "Replaced entire file content", content);
            }
        } catch (IOException e) {
            return new EditorResult(false, "Edit error: " + e.getMessage(), null);
        }
    }

    private EditorResult saveFile(Path path, ToolRequest request) {
        try {
            String content = (String) request.arguments().get("content");
            if (content != null) {
                Files.writeString(path, content);
            }
            return new EditorResult(true, "File saved", content);
        } catch (IOException e) {
            return new EditorResult(false, "Save error: " + e.getMessage(), null);
        }
    }
}
