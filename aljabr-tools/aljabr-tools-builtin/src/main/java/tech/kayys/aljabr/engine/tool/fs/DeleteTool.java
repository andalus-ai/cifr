package tech.kayys.aljabr.engine.tool.fs;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.tool.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

@ApplicationScoped
public class DeleteTool implements AljabrTool {

    private static final Path WORKSPACE_ROOT = Paths.get(System.getProperty("user.dir"));

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
            "delete",
            "Delete files or directories from the workspace.",
            ToolCategory.FILESYSTEM,
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "paths", Map.of("type", "array", "description", "List of paths to delete",
                        "items", Map.of("type", "string")),
                    "recursive", Map.of("type", "boolean", "description", "Delete directories recursively"),
                    "force", Map.of("type", "boolean", "description", "Ignore missing files")
                ),
                "required", List.of("paths")
            ),
            Set.of(ToolCapability.WRITE, ToolCapability.DELETE, ToolCapability.MUTATES_WORKSPACE),
            ToolRisk.CRITICAL
        );
    }

    @Override
    public Uni<ToolResult> execute(ToolRequest request, ToolExecutionContext context) {
        return Uni.createFrom().item(() -> {
            @SuppressWarnings("unchecked")
            List<String> pathStrs = (List<String>) request.arguments().get("paths");
            boolean recursive = Boolean.TRUE.equals(request.arguments().get("recursive"));
            boolean force = Boolean.TRUE.equals(request.arguments().get("force"));

            if (pathStrs == null || pathStrs.isEmpty())
                return new DeleteResult(false, "No paths specified", List.of(), List.of());

            List<DeleteRecord> deleted = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            for (String pathStr : pathStrs) {
                Path abs = WORKSPACE_ROOT.resolve(pathStr).normalize();
                if (!abs.startsWith(WORKSPACE_ROOT)) { errors.add("Denied: " + pathStr); continue; }
                if (!Files.exists(abs)) {
                    if (!force) errors.add("Not found: " + pathStr);
                    continue;
                }
                try {
                    boolean isDir = Files.isDirectory(abs);
                    if (isDir && !recursive) { errors.add("Is directory, use recursive: " + pathStr); continue; }
                    long size = isDir ? 0 : Files.size(abs);
                    int count = 0;
                    if (isDir) {
                        try (Stream<Path> walk = Files.walk(abs)) {
                            count = (int) walk.sorted(Comparator.reverseOrder()).peek(p -> {
                                try { Files.deleteIfExists(p); } catch (IOException e) { throw new RuntimeException(e); }
                            }).count();
                        }
                    } else {
                        Files.delete(abs);
                        count = 1;
                    }
                    deleted.add(new DeleteRecord(pathStr, isDir, count, size));
                } catch (IOException e) {
                    errors.add("Failed: " + pathStr + " — " + e.getMessage());
                }
            }

            String msg = "Deleted " + deleted.size() + " item(s)" + (errors.isEmpty() ? "" : ", " + errors.size() + " errors");
            return new DeleteResult(errors.isEmpty(), msg, deleted, errors);
        });
    }
}
