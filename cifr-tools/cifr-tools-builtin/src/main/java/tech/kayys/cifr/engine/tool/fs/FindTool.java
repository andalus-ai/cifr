package tech.kayys.cifr.engine.tool.fs;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.tool.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

@ApplicationScoped
public class FindTool implements CifrTool {

    private static final Path WORKSPACE_ROOT = Paths.get(System.getProperty("user.dir"));
    private static final int DEFAULT_LIMIT = 100;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
            "find",
            "Find files in the workspace by name pattern, size, or modification time.",
            ToolCategory.SEARCH,
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "path", Map.of("type", "string", "description", "Root directory to search"),
                    "pattern", Map.of("type", "string", "description", "Glob name pattern (e.g., *.java)"),
                    "type", Map.of("type", "string", "enum", List.of("file", "directory", "any"),
                        "description", "Entry type filter"),
                    "minSize", Map.of("type", "integer", "description", "Minimum size in bytes"),
                    "maxSize", Map.of("type", "integer", "description", "Maximum size in bytes"),
                    "modifiedAfter", Map.of("type", "string", "description", "ISO-8601 datetime filter"),
                    "maxDepth", Map.of("type", "integer", "description", "Max traversal depth"),
                    "limit", Map.of("type", "integer", "description", "Maximum results")
                ),
                "required", List.of("pattern")
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
                String typeFilter = (String) request.arguments().getOrDefault("type", "any");
                long minSize = ((Number) request.arguments().getOrDefault("minSize", 0)).longValue();
                long maxSize = ((Number) request.arguments().getOrDefault("maxSize", Long.MAX_VALUE)).longValue();
                int maxDepth = ((Number) request.arguments().getOrDefault("maxDepth", 10)).intValue();
                int limit = ((Number) request.arguments().getOrDefault("limit", DEFAULT_LIMIT)).intValue();
                String modifiedAfterStr = (String) request.arguments().get("modifiedAfter");
                Instant modifiedAfter = modifiedAfterStr != null ? Instant.parse(modifiedAfterStr) : Instant.EPOCH;

                Path searchRoot = WORKSPACE_ROOT.resolve(pathStr).normalize();
                if (!searchRoot.startsWith(WORKSPACE_ROOT) || !Files.exists(searchRoot))
                    return new FindResult(pattern, List.of(), false);

                PathMatcher nameMatcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
                List<FindEntry> entries = new ArrayList<>();
                boolean truncated = false;

                try (Stream<Path> stream = Files.walk(searchRoot, maxDepth)) {
                    List<Path> paths = stream
                        .filter(p -> !p.equals(searchRoot))
                        .filter(p -> nameMatcher.matches(p.getFileName()))
                        .filter(p -> {
                            if ("file".equals(typeFilter)) return Files.isRegularFile(p);
                            if ("directory".equals(typeFilter)) return Files.isDirectory(p);
                            return true;
                        })
                        .toList();

                    for (Path p : paths) {
                        if (entries.size() >= limit) { truncated = true; break; }
                        BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                        long size = attrs.isDirectory() ? 0 : attrs.size();
                        Instant modified = attrs.lastModifiedTime().toInstant();

                        if (size < minSize || size > maxSize) continue;
                        if (modified.isBefore(modifiedAfter)) continue;

                        entries.add(new FindEntry(
                            searchRoot.relativize(p).toString(),
                            attrs.isDirectory(),
                            size,
                            modified.toEpochMilli()
                        ));
                    }
                }

                return new FindResult(pattern, entries, truncated);

            } catch (IOException e) {
                return new FindResult("", List.of(), false);
            }
        });
    }
}
