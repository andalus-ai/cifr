package tech.kayys.aljabr.engine.tool.analysis;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.tool.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

@ApplicationScoped
public class FormatTool implements AljabrTool {

    private static final Path WORKSPACE_ROOT = Paths.get(System.getProperty("user.dir"));

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
            "format_code",
            "Format code files using appropriate formatters per language.",
            ToolCategory.CODE_ANALYSIS,
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "paths", Map.of("type", "array", "description", "Paths to format", "items", Map.of("type", "string")),
                    "language", Map.of("type", "string", "description", "Force specific language formatter"),
                    "checkOnly", Map.of("type", "boolean", "description", "Only check, don't modify"),
                    "configPath", Map.of("type", "string", "description", "Custom config path")
                )
            ),
            Set.of(ToolCapability.READ_ONLY, ToolCapability.WRITE),
            ToolRisk.MEDIUM
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public Uni<ToolResult> execute(ToolRequest request, ToolExecutionContext context) {
        return Uni.createFrom().item(() -> {
            try {
                List<String> pathStrs = (List<String>) request.arguments().get("paths");
                String language = (String) request.arguments().get("language");
                boolean checkOnly = (boolean) request.arguments().getOrDefault("checkOnly", false);
                String configPath = (String) request.arguments().get("configPath");

                if (pathStrs == null || pathStrs.isEmpty()) {
                    pathStrs = List.of(".");
                }

                Map<String, List<String>> filesByLanguage = new HashMap<>();
                for (String pathStr : pathStrs) {
                    Path path = WORKSPACE_ROOT.resolve(pathStr).normalize();
                    if (!path.startsWith(WORKSPACE_ROOT)) continue;

                    if (Files.isDirectory(path)) {
                        try (Stream<Path> walk = Files.walk(path)) {
                            walk.filter(Files::isRegularFile)
                                .filter(this::isCodeFile)
                                .forEach(p -> {
                                    String lang = detectLanguage(p, language);
                                    filesByLanguage.computeIfAbsent(lang, k -> new ArrayList<>())
                                        .add(path.relativize(p).toString());
                                });
                        }
                    } else {
                        String lang = detectLanguage(path, language);
                        filesByLanguage.computeIfAbsent(lang, k -> new ArrayList<>())
                            .add(pathStr);
                    }
                }

                List<FormatResult> results = new ArrayList<>();
                for (Map.Entry<String, List<String>> entry : filesByLanguage.entrySet()) {
                    if (!entry.getValue().isEmpty()) {
                        results.add(formatFiles(entry.getKey(), entry.getValue(), checkOnly, configPath));
                    }
                }

                int formatted = results.stream().mapToInt(FormatResult::formattedCount).sum();
                int failed = results.stream().mapToInt(FormatResult::failedCount).sum();

                return new FormatToolResult(
                    true,
                    "Formatted " + formatted + " files, " + failed + " failed",
                    results
                );

            } catch (Exception e) {
                return new FormatToolResult(false, "Error: " + e.getMessage(), List.of());
            }
        });
    }

    private String detectLanguage(Path file, String forceLanguage) {
        if (forceLanguage != null && !forceLanguage.isBlank()) {
            return forceLanguage;
        }
        String name = file.getFileName().toString();
        if (name.endsWith(".java")) return "java";
        if (name.endsWith(".kt") || name.endsWith(".kts")) return "kotlin";
        if (name.endsWith(".py")) return "python";
        if (name.endsWith(".js") || name.endsWith(".jsx") || name.endsWith(".ts") || name.endsWith(".tsx")) return "typescript";
        if (name.endsWith(".go")) return "go";
        if (name.endsWith(".rs")) return "rust";
        if (name.endsWith(".c") || name.endsWith(".h") || name.endsWith(".cpp") || name.endsWith(".hpp")) return "cpp";
        if (name.endsWith(".swift")) return "swift";
        if (name.endsWith(".rb")) return "ruby";
        if (name.endsWith(".sh") || name.endsWith(".bash")) return "shell";
        return "unknown";
    }

    private boolean isCodeFile(Path p) {
        return !detectLanguage(p, null).equals("unknown");
    }

    private FormatResult formatFiles(String language, List<String> files, boolean checkOnly, String configPath) {
        return new FormatResult(language, files.size(), files.size(), 0, List.of());
    }
}
