package tech.kayys.aljabr.engine;

import tech.kayys.aljabr.skill.DartPackageUriResolver;

import tech.kayys.wayang.tools.spi.Tool;
import tech.kayys.wayang.tools.spi.ToolContext;
import tech.kayys.wayang.tools.spi.ToolResult;
import tech.kayys.wayang.rag.core.spi.RagCliFacade;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent tools backed by the RAG facade.
 *
 * <h2>Available tools</h2>
 * <ul>
 * <li>{@code semantic_search} — natural-language codebase search via TF-IDF
 * (community)
 * or vector embeddings (pro with full CDI stack).</li>
 * <li>{@code read_file} — read a file in the workspace by relative path.</li>
 * <li>{@code list_files} — list files in a directory, optionally filtered by
 * glob.</li>
 * <li>{@code search_files} — keyword/regex grep across the workspace.</li>
 * </ul>
 */
public final class AljabrWorkspaceTools {

    private AljabrWorkspaceTools() {
    }

    /**
     * Returns the full list of RAG-backed tools to register on the agent.
     *
     * @param facade    the RAG facade (may be {@link StandaloneRagCliFacade} or the
     *                  full CDI-backed impl)
     * @param workspace absolute path to the project workspace root
     */
    public static List<Tool> getTools(RagCliFacade facade, Path workspace) {
        return List.of(
                new SemanticSearchTool(facade),
                new ReadFileTool(workspace),
                new ListFilesTool(workspace),
                new SearchFilesTool(workspace));
    }

    /** Backward-compat overload (workspace resolved lazily from CWD). */
    public static List<Tool> getTools(RagCliFacade facade) {
        return getTools(facade, Path.of(".").toAbsolutePath().normalize());
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
                    PREFER this tool over read_file or list_files when you need to understand
                    how something works or where to find it.
                    Examples:
                      - "How is authentication implemented?"
                      - "Where is the RagCliFacade interface defined?"
                      - "Find all usages of semantic_search"
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

        ReadFileTool(Path workspace) {
            this.workspace = workspace.toAbsolutePath().normalize();
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
            DartPackageUriResolver resolver = new DartPackageUriResolver(workspace);
            Optional<DartPackageUriResolver.ResolvedDartPackageUri> packageFile = Optional.empty();
            Path target;
            if (relPath.startsWith("package:")) {
                packageFile = resolver.resolve(relPath);
                if (packageFile.isEmpty()) {
                    return ToolResult.error(resolver.describeResolutionFailure(relPath));
                }
                target = packageFile.get().file();
            } else {
                target = workspace.resolve(relPath).normalize();
                if (!target.startsWith(workspace)) {
                    return ToolResult.error("Path escapes workspace root: " + relPath);
                }
            }
            if (!Files.exists(target)) {
                String resolvedPath = packageFile
                        .map(file -> " (resolved to " + file.file().toString() + ")")
                        .orElse("");
                return ToolResult.error("File not found: " + relPath + resolvedPath);
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
                packageFile.ifPresent(file -> sb.append("Resolved ")
                        .append(file.raw())
                        .append(" via ")
                        .append(workspace.relativize(file.packageConfig()))
                        .append(" -> ")
                        .append(file.file())
                        .append("\n"));
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
            return """
                    List files and directories under a workspace path.
                    Returns relative paths. Use glob (e.g. "**/*.java") to filter.
                    """;
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
                if (entries.isEmpty()) {
                    return ToolResult.success("(empty directory or no files matching the glob)");
                }
                Collections.sort(entries);
                return ToolResult.success(String.join("\n", entries));
            } catch (Exception e) {
                return ToolResult.error("Failed to list files: " + e.getMessage());
            }
        }
    }

    // =========================================================================
    // search_files (keyword/regex grep)
    // =========================================================================

    public static final class SearchFilesTool implements Tool {
        private static final int MAX_RESULTS = 80;
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
            return """
                    Grep/search for a keyword or regex pattern across files in the workspace.
                    Returns matching lines with file path and line number.
                    Use 'path' to scope the search to a subdirectory.
                    Use 'glob' to restrict file types, e.g. "**/*.java".
                    """;
        }

        @Override
        public Map<String, Object> inputSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.ofEntries(
                            Map.entry("pattern", Map.of("type", "string",
                                    "description", "Keyword or regex pattern to search for")),
                            Map.entry("path", Map.of("type", "string",
                                    "description", "Subdirectory to scope search (default: workspace root)")),
                            Map.entry("glob", Map.of("type", "string",
                                    "description", "File glob filter, e.g. '**/*.java' (optional)")),
                            Map.entry("case_insensitive", Map.of("type", "boolean",
                                    "description", "If true, match case-insensitively (default: true)"))),
                    "required", List.of("pattern"));
        }

        @Override
        public ToolResult execute(Map<String, Object> params, ToolContext context) {
            String pattern = (String) params.get("pattern");
            if (pattern == null || pattern.isBlank()) {
                return ToolResult.error("pattern is required");
            }
            String relPath = (String) params.getOrDefault("path", ".");
            String glob = (String) params.getOrDefault("glob", null);
            boolean caseInsensitive = !(Boolean.FALSE.equals(params.get("case_insensitive")));

            Path searchRoot = workspace.resolve(relPath != null ? relPath : ".").normalize();
            if (!searchRoot.startsWith(workspace)) {
                return ToolResult.error("Path escapes workspace root: " + relPath);
            }

            int flags = caseInsensitive ? java.util.regex.Pattern.CASE_INSENSITIVE : 0;
            java.util.regex.Pattern regex;
            try {
                regex = java.util.regex.Pattern.compile(pattern, flags);
            } catch (Exception e) {
                // Fall back to literal match
                regex = java.util.regex.Pattern.compile(
                        java.util.regex.Pattern.quote(pattern), flags);
            }

            final java.util.regex.Pattern finalRegex = regex;
            final PathMatcher globMatcher = glob != null && !glob.isBlank()
                    ? workspace.getFileSystem().getPathMatcher("glob:" + glob)
                    : null;

            List<String> results = new ArrayList<>();
            try {
                Files.walkFileTree(searchRoot, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (Thread.currentThread().isInterrupted()) {
                            return FileVisitResult.TERMINATE;
                        }
                        String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                        if (name.equals("target") || name.equals("build") || name.equals("node_modules")
                                || name.equals(".git") || name.equals("__pycache__")) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (Thread.currentThread().isInterrupted()) {
                            return FileVisitResult.TERMINATE;
                        }
                        if (results.size() >= MAX_RESULTS)
                            return FileVisitResult.TERMINATE;
                        if (attrs.size() > 512_000)
                            return FileVisitResult.CONTINUE;
                        if (globMatcher != null) {
                            Path rel = searchRoot.relativize(file);
                            if (!globMatcher.matches(rel) && !globMatcher.matches(file.getFileName())) {
                                return FileVisitResult.CONTINUE;
                            }
                        }
                        try {
                            String[] lines = Files.readString(file, StandardCharsets.UTF_8).split("\\r?\\n", -1);
                            for (int i = 0; i < lines.length && results.size() < MAX_RESULTS; i++) {
                                if (finalRegex.matcher(lines[i]).find()) {
                                    results.add(String.format("%s:%d: %s",
                                            workspace.relativize(file), i + 1, lines[i].trim()));
                                }
                            }
                        } catch (Exception ignored) {
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                return ToolResult.error("Search failed: " + e.getMessage());
            }

            if (results.isEmpty()) {
                return ToolResult.success("No matches found for pattern: " + pattern);
            }
            String header = String.format("Found %d match(es):\n", results.size());
            return ToolResult.success(header + String.join("\n", results));
        }
    }
}
