package tech.kayys.cifr.engine.tool.fs;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.tool.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@ApplicationScoped
public class SearchTool implements CifrTool {
    private static final int DEFAULT_LIMIT = 50;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
            "search",
            "Search for text within workspace files using substring or regex matching.",
            ToolCategory.SEARCH,
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "query", Map.of("type", "string", "description", "Text or pattern to search"),
                    "isRegex", Map.of("type", "boolean", "description", "Treat query as regular expression"),
                    "caseSensitive", Map.of("type", "boolean", "description", "Case-sensitive search"),
                    "path", Map.of("type", "string", "description", "Subdirectory to limit search"),
                    "limit", Map.of("type", "integer", "description", "Max results to return")
                ),
                "required", List.of("query")
            ),
            Set.of(ToolCapability.READ_ONLY),
            ToolRisk.NONE
        );
    }

    @Override
    public Uni<ToolResult> execute(ToolRequest request, ToolExecutionContext context) {
        return Uni.createFrom().item(() -> {
            String query = (String) request.arguments().get("query");
            if (query == null || query.isBlank()) {
                return new SearchResult("", List.of(), false);
            }

            boolean isRegex = (boolean) request.arguments().getOrDefault("isRegex", false);
            boolean caseSensitive = (boolean) request.arguments().getOrDefault("caseSensitive", false);
            String subPath = (String) request.arguments().getOrDefault("path", ".");
            int limit = ((Number) request.arguments().getOrDefault("limit", DEFAULT_LIMIT)).intValue();

            Path searchRoot = context.resolveWorkspacePath(subPath);
            if (!searchRoot.startsWith(WORKSPACE_ROOT) || !Files.exists(searchRoot)) {
                return new SearchResult(query, List.of(), false);
            }

            Pattern pattern;
            try {
                int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                pattern = isRegex ? Pattern.compile(query, flags) : Pattern.compile(Pattern.quote(query), flags);
            } catch (Exception e) {
                return new SearchResult(query, List.of(), false);
            }

            List<SearchMatch> matches = new ArrayList<>();
            boolean truncated = false;

            try (Stream<Path> walk = Files.walk(searchRoot, 10)) {
                List<Path> files = walk.filter(Files::isRegularFile)
                    .filter(p -> !p.toString().contains("/.git/") && !p.toString().contains("/target/"))
                    .toList();

                for (Path file : files) {
                    if (matches.size() >= limit) {
                        truncated = true;
                        break;
                    }
                    try {
                        List<String> lines = Files.readAllLines(file);
                        for (int i = 0; i < lines.size(); i++) {
                            String line = lines.get(i);
                            if (pattern.matcher(line).find()) {
                                matches.add(new SearchMatch(
                                    context.requireWorkspaceRoot().relativize(file).toString(),
                                    i + 1,
                                    line.trim()
                                ));
                                if (matches.size() >= limit) {
                                    truncated = true;
                                    break;
                                }
                            }
                        }
                    } catch (IOException ignored) {
                        // Skip binary or unreadable files
                    }
                }
            } catch (IOException e) {
                return new SearchResult(query, List.of(), false);
            }

            return new SearchResult(query, matches, truncated);
        });
    }
}
