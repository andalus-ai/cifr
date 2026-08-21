package tech.kayys.aljabr.engine.tool.fs;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.tool.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;

@ApplicationScoped
public class DirectoryListTool implements AljabrTool {

    private static final Path WORKSPACE_ROOT = Paths.get(System.getProperty("user.dir"));
    private static final int DEFAULT_MAX_DEPTH = 1;
    private static final int MAX_ENTRIES = 500;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
            "list_directory",
            "List files and subdirectories in a workspace directory with metadata and optional glob filter.",
            ToolCategory.FILESYSTEM,
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "path", Map.of("type", "string", "description", "Directory to list"),
                    "pattern", Map.of("type", "string", "description", "Glob pattern filter (e.g., *.java)"),
                    "recursive", Map.of("type", "boolean", "description", "List recursively"),
                    "maxDepth", Map.of("type", "integer", "description", "Max recursion depth"),
                    "showHidden", Map.of("type", "boolean", "description", "Include hidden files")
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
                String pathStr = (String) request.arguments().getOrDefault("path", ".");
                String pattern = (String) request.arguments().getOrDefault("pattern", "*");
                boolean recursive = Boolean.TRUE.equals(request.arguments().get("recursive"));
                int maxDepth = ((Number) request.arguments().getOrDefault("maxDepth",
                    recursive ? 10 : DEFAULT_MAX_DEPTH)).intValue();
                boolean showHidden = Boolean.TRUE.equals(request.arguments().get("showHidden"));

                Path dir = WORKSPACE_ROOT.resolve(pathStr).normalize();
                if (!dir.startsWith(WORKSPACE_ROOT) || !Files.isDirectory(dir))
                    return new DirectoryListResult(pathStr, List.of(), false);

                PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
                List<DirectoryEntry> entries = new ArrayList<>();
                boolean truncated = false;

                try (Stream<Path> stream = Files.walk(dir, maxDepth)) {
                    List<Path> paths = stream
                        .filter(p -> !p.equals(dir))
                        .filter(p -> showHidden || !p.getFileName().toString().startsWith("."))
                        .filter(p -> Files.isDirectory(p) || matcher.matches(p.getFileName()))
                        .sorted()
                        .toList();

                    for (Path p : paths) {
                        if (entries.size() >= MAX_ENTRIES) { truncated = true; break; }
                        boolean isDir = Files.isDirectory(p);
                        BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                        long size = isDir ? 0 : attrs.size();
                        long modified = attrs.lastModifiedTime().toMillis();
                        entries.add(new DirectoryEntry(
                            dir.relativize(p).toString(),
                            isDir,
                            size,
                            modified
                        ));
                    }
                }

                return new DirectoryListResult(pathStr, entries, truncated);

            } catch (IOException e) {
                return new DirectoryListResult("", List.of(), false);
            }
        });
    }
}
