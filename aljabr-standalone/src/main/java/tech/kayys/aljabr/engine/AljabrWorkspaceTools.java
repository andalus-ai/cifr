package tech.kayys.aljabr.engine;

import tech.kayys.aljabr.skill.DartPackageUriResolver;
import tech.kayys.aljabr.skill.WorkspaceUriResolver;

import tech.kayys.andalus.tools.spi.Tool;
import tech.kayys.andalus.tools.spi.ToolContext;
import tech.kayys.andalus.tools.spi.ToolResult;
import tech.kayys.andalus.rag.core.spi.RagCliFacade;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent tools backed by the RAG facade.
 */
public final class AljabrWorkspaceTools {

    private AljabrWorkspaceTools() {
    }

    public static List<Tool> getTools(RagCliFacade facade, Path workspace, List<WorkspaceUriResolver> resolvers) {
        return List.of(
                new SemanticSearchTool(facade),
                new ReadFileTool(workspace, resolvers),
                new ListFilesTool(workspace),
                new SearchFilesTool(workspace));
    }

    public static List<Tool> getTools(RagCliFacade facade, Path workspace) {
        return getTools(facade, workspace, List.of(new DartPackageUriResolver(workspace)));
    }

    public static List<Tool> getTools(RagCliFacade facade) {
        Path ws = Path.of(".").toAbsolutePath().normalize();
        return getTools(facade, ws, List.of(new DartPackageUriResolver(ws)));
    }

    // =========================================================================
    // semantic_search
    // =========================================================================

    public static final class SemanticSearchTool implements Tool {
        private final RagCliFacade facade;

        SemanticSearchTool(RagCliFacade facade) {
            this.facade = facade;
        }

        @Override
        public String id() {
            return "semantic_search";
        }

        @Override
        public String name() {
            return "semantic_search";
        }

        @Override
        public String description() {
            return """
                    Search the entire codebase using natural language.
                    Returns the most relevant files and code snippets for the given query.
                    """;
        }

        @Override
        public Map<String, Object> inputSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "query", Map.of(
                                    "type", "string",
                                    "description", "Natural language query to search the codebase")),
                    "required", List.of("query"));
        }

        @Override
        public ToolResult execute(Map<String, Object> params, ToolContext context) {
            String query = (String) params.get("query");
            if (query == null || query.isBlank()) {
                return ToolResult.error("query is required");
            }
            try {
                String answer = facade.querySync("default", query, "default");
                return ToolResult.success(answer);
            } catch (Exception e) {
                return ToolResult.error("Semantic search failed: " + e.getMessage());
            }
        }
    }

    // =========================================================================
    // read_file
    // =========================================================================

    public static final class ReadFileTool implements Tool {
        private final Path workspace;
        private final List<WorkspaceUriResolver> resolvers;

        ReadFileTool(Path workspace, List<WorkspaceUriResolver> resolvers) {
            this.workspace = workspace.toAbsolutePath().normalize();
            this.resolvers = resolvers != null ? resolvers : List.of();
        }

        ReadFileTool(Path workspace) {
            this(workspace, List.of(new DartPackageUriResolver(workspace)));
        }

        @Override
        public String id() {
            return "read_file";
        }

        @Override
        public String name() {
            return "read_file";
        }

        @Override
        public String description() {
            return """
                    Read the content of a file in the workspace.
                    Provide a path relative to the workspace root.
                    Dart/Flutter package URIs such as package:riverpod/src/framework/element.dart
                    are also supported when .dart_tool/package_config.json is available.
                    Optionally specify start_line / end_line (1-based, inclusive) to read a slice.
                    """;
        }

        @Override
        public Map<String, Object> inputSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.ofEntries(
                            Map.entry("path", Map.of("type", "string",
                                    "description", "Relative path from workspace root, or a Dart package: URI")),
                            Map.entry("start_line", Map.of("type", "integer",
                                    "description", "First line to read (1-based, optional)")),
                            Map.entry("end_line", Map.of("type", "integer",
                                    "description", "Last line to read inclusive (1-based, optional)"))),
                    "required", List.of("path"));
        }

        @Override
        public ToolResult execute(Map<String, Object> params, ToolContext context) {
            String relPath = (String) params.get("path");
            if (relPath == null || relPath.isBlank()) {
                return ToolResult.error("path is required");
            }

            Path target = null;
            String diagnosticMessage = null;

            for (WorkspaceUriResolver resolver : resolvers) {
                if (resolver.canResolve(relPath)) {
                    Optional<Path> resolved = resolver.resolve(relPath);
                    if (resolved.isPresent()) {
                        target = resolved.get();
                        break;
                    } else {
                        diagnosticMessage = resolver.describeFailure(relPath);
                    }
                }
            }

            if (target == null) {
                if (diagnosticMessage != null) {
                    return ToolResult.error(diagnosticMessage);
                }
                target = workspace.resolve(relPath).normalize();
                if (!target.startsWith(workspace)) {
                    return ToolResult.error("Path escapes workspace root: " + relPath);
                }
            }

            if (!Files.exists(target)) {
                return ToolResult.error("File not found: " + relPath + " (resolved to " + target + ")");
            }
            if (Files.isDirectory(target)) {
                return ToolResult.error("Path is a directory; use list_files instead: " + relPath);
            }
            try {
                String[] lines = Files.readString(target, StandardCharsets.UTF_8).split("\\r?\\n", -1);
                int from = 1;
                int to = lines.length;
                Object startObj = params.get("start_line");
                Object endObj = params.get("end_line");
                if (startObj instanceof Number n)
                    from = Math.max(1, n.intValue());
                if (endObj instanceof Number n)
                    to = Math.min(lines.length, n.intValue());

                StringBuilder sb = new StringBuilder();
                sb.append("```\n");
                for (int i = from; i <= to; i++) {
                    sb.append(String.format("%4d | %s%n", i, lines[i - 1]));
                }
                sb.append("```\n");
                return ToolResult.success(sb.toString());
            } catch (Exception e) {
                return ToolResult.error("Failed to read file: " + e.getMessage());
            }
        }
    }

    // =========================================================================
    // list_files
    // =========================================================================

    public static final class ListFilesTool implements Tool {
        private static final int MAX_ENTRIES = 200;
        private final Path workspace;

        ListFilesTool(Path workspace) {
            this.workspace = workspace;
        }

        @Override
        public String id() {
            return "list_files";
        }

        @Override
        public String name() {
            return "list_files";
        }

        @Override
        public String description() {
            return "List files and directories under a workspace path.";
        }

        @Override
        public Map<String, Object> inputSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.ofEntries(
                            Map.entry("path", Map.of("type", "string",
                                    "description", "Directory path relative to workspace root (default: '.')")),
                            Map.entry("glob", Map.of("type", "string",
                                    "description", "Glob pattern to filter, e.g. '**/*.java' (optional)"))),
                    "required", List.of());
        }

        @Override
        public ToolResult execute(Map<String, Object> params, ToolContext context) {
            String relPath = (String) params.getOrDefault("path", ".");
            String glob = (String) params.getOrDefault("glob", null);
            Path dir = workspace.resolve(relPath != null ? relPath : ".").normalize();
            if (!dir.startsWith(workspace)) {
                return ToolResult.error("Path escapes workspace root: " + relPath);
            }
            if (!Files.exists(dir)) {
                return ToolResult.error("Directory not found: " + relPath);
            }

            try {
                List<String> entries = new ArrayList<>();
                if (glob != null && !glob.isBlank()) {
                    PathMatcher matcher = dir.getFileSystem().getPathMatcher("glob:" + glob);
                    Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (Thread.currentThread().isInterrupted()) {
                                return FileVisitResult.TERMINATE;
                            }
                            Path rel = dir.relativize(file);
                            if (matcher.matches(rel) || matcher.matches(file.getFileName())) {
                                entries.add(workspace.relativize(file).toString());
                            }
                            return entries.size() < MAX_ENTRIES
                                    ? FileVisitResult.CONTINUE
                                    : FileVisitResult.TERMINATE;
                        }
                    });
                } else {
                    try (var stream = Files.list(dir)) {
                        stream.limit(MAX_ENTRIES).forEach(p -> {
                            String name = workspace.relativize(p).toString();
                            entries.add(Files.isDirectory(p) ? name + "/" : name);
                        });
                    }
                }
                Collections.sort(entries);
                return ToolResult.success(String.join("\n", entries));
            } catch (Exception e) {
                return ToolResult.error("Failed to list files: " + e.getMessage());
            }
        }
    }

    // =========================================================================
    // search_files
    // =========================================================================

    public static final class SearchFilesTool implements Tool {
        private static final int MAX_RESULTS = 50;
        private final Path workspace;

        SearchFilesTool(Path workspace) {
            this.workspace = workspace;
        }

        @Override
        public String id() {
            return "search_files";
        }

        @Override
        public String name() {
            return "search_files";
        }

        @Override
        public String description() {
            return "Search file contents across the workspace by regex pattern.";
        }

        @Override
        public Map<String, Object> inputSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "pattern", Map.of("type", "string", "description", "Regex pattern to search for"),
                            "path", Map.of("type", "string", "description", "Subdirectory to search (optional, default: '.')")),
                    "required", List.of("pattern"));
        }

        @Override
        public ToolResult execute(Map<String, Object> params, ToolContext context) {
            String patternStr = (String) params.get("pattern");
            if (patternStr == null || patternStr.isBlank()) {
                return ToolResult.error("pattern is required");
            }
            String subPath = (String) params.getOrDefault("path", ".");
            Path start = workspace.resolve(subPath != null ? subPath : ".").normalize();
            if (!start.startsWith(workspace) || !Files.exists(start)) {
                return ToolResult.error("Directory not found or escapes workspace: " + subPath);
            }

            try {
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(patternStr, java.util.regex.Pattern.CASE_INSENSITIVE);
                List<String> results = new ArrayList<>();

                Files.walkFileTree(start, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                        if (name.startsWith(".") || name.equals("node_modules") || name.equals("target") || name.equals("build")) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (results.size() >= MAX_RESULTS) return FileVisitResult.TERMINATE;
                        try {
                            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                            for (int i = 0; i < lines.size(); i++) {
                                if (pattern.matcher(lines.get(i)).find()) {
                                    results.add(workspace.relativize(file) + ":" + (i + 1) + ": " + lines.get(i).trim());
                                    if (results.size() >= MAX_RESULTS) return FileVisitResult.TERMINATE;
                                }
                            }
                        } catch (Exception ignored) {
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });

                return ToolResult.success(results.isEmpty() ? "No matches found." : String.join("\n", results));
            } catch (Exception e) {
                return ToolResult.error("Failed to search files: " + e.getMessage());
            }
        }
    }
}
