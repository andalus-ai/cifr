package tech.kayys.cifr.engine.tool.fs;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.tool.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@ApplicationScoped
public class BatchWriteTool implements CifrTool {

    private static final int MAX_FILES_PER_BATCH = 100;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
            "batch_write",
            "Write multiple files in a single operation with rollback on failure.",
            ToolCategory.FILESYSTEM,
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "files", Map.of("type", "array", "description", "List of {path, content, append} file specs"),
                    "atomic", Map.of("type", "boolean", "description", "Rollback all if any file fails")
                ),
                "required", List.of("files")
            ),
            Set.of(ToolCapability.WRITE, ToolCapability.MUTATES_WORKSPACE),
            ToolRisk.HIGH
        );
    }

    @Override
    public Uni<ToolResult> execute(ToolRequest request, ToolExecutionContext context) {
        return Uni.createFrom().item(() -> {
            List<Map<String, Object>> fileSpecs = castList(request.arguments().get("files"));
            boolean atomic = Boolean.TRUE.equals(request.arguments().getOrDefault("atomic", true));

            if (fileSpecs == null || fileSpecs.isEmpty()) {
                return new BatchWriteResult(false, "No files specified", List.of(), List.of());
            }
            if (fileSpecs.size() > MAX_FILES_PER_BATCH) {
                return new BatchWriteResult(false, "Too many files (max " + MAX_FILES_PER_BATCH + ")", List.of(), List.of());
            }

            Map<Path, String> backups = new LinkedHashMap<>();
            List<FileWriteResult> results = new ArrayList<>();

            try {
                // Validate and backup
                for (Map<String, Object> spec : fileSpecs) {
                    Path abs = WorkspaceResolver.resolveWorkspacePath(context, (String) spec.get("path"));
                    if (!abs.startsWith(WORKSPACE_ROOT)) throw new SecurityException("Path outside workspace: " + spec.get("path"));
                    if (atomic && Files.exists(abs)) backups.put(abs, Files.readString(abs));
                }

                // Write
                for (Map<String, Object> spec : fileSpecs) {
                    String pathStr = (String) spec.get("path");
                    String content = (String) spec.getOrDefault("content", "");
                    boolean append = Boolean.TRUE.equals(spec.get("append"));
                    Path abs = WorkspaceResolver.resolveWorkspacePath(context, pathStr);
                    if (abs.getParent() != null) Files.createDirectories(abs.getParent());
                    if (append && Files.exists(abs)) {
                        Files.writeString(abs, content, StandardOpenOption.APPEND);
                    } else {
                        Files.writeString(abs, content);
                    }
                    results.add(new FileWriteResult(pathStr, true, "OK", Files.size(abs), Files.getLastModifiedTime(abs).toMillis()));
                }

                return new BatchWriteResult(true, "All " + results.size() + " files written", results, List.of());

            } catch (Exception e) {
                if (atomic) {
                    for (Map.Entry<Path, String> entry : backups.entrySet()) {
                        try {
                            if (entry.getValue() != null) Files.writeString(entry.getKey(), entry.getValue());
                            else Files.deleteIfExists(entry.getKey());
                        } catch (IOException ignored) {}
                    }
                    return new BatchWriteResult(false, "Batch rolled back: " + e.getMessage(), List.of(),
                        List.of(new FileWriteResult("", false, e.getMessage(), 0, 0)));
                }
                return new BatchWriteResult(false, "Batch failed: " + e.getMessage(), results, List.of());
            }
        });
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object o) {
        if (o instanceof List<?> list) return (List<Map<String, Object>>) list;
        return null;
    }
}
