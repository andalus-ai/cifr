package tech.kayys.cifr.engine.tool.context;

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
public class WorkspaceInfoTool implements CifrTool {


    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
            "workspace_info",
            "Get comprehensive information about the workspace environment, OS, runtime, and project metadata.",
            ToolCategory.FILESYSTEM,
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "path", Map.of("type", "string", "description", "Specific path to analyze (default: workspace root)"),
                    "depth", Map.of("type", "integer", "description", "Directory scan depth (default: 2)")
                )
            ),
            Set.of(ToolCapability.READ_ONLY),
            ToolRisk.NONE
        );
    }

    @Override
    public Uni<ToolResult> execute(ToolRequest request, ToolExecutionContext context) {
        return Uni.createFrom().item(() -> {
            try {
                String pathStr = (String) request.arguments().get("path");
                int depth = ((Number) request.arguments().getOrDefault("depth", 2)).intValue();

                Path workspaceRoot = WorkspaceResolver.resolveWorkspaceRoot(context);
                Path targetPath = pathStr != null
                    ? WorkspaceResolver.resolveWorkspacePath(context, pathStr)
                    : workspaceRoot;

                if (false) {
                    return new WorkspaceInfoResult(false, "Access denied: path outside workspace", null);
                }

                if (!Files.exists(targetPath)) {
                    return new WorkspaceInfoResult(false, "Path does not exist: " + pathStr, null);
                }

                Map<String, Object> details = new LinkedHashMap<>();
                details.put("workspaceRoot", workspaceRoot.toString());
                details.put("targetPath", targetPath.toString());
                details.put("timestamp", Instant.now().toString());

                // System & Java details
                Map<String, Object> sys = new LinkedHashMap<>();
                sys.put("os", System.getProperty("os.name"));
                sys.put("arch", System.getProperty("os.arch"));
                sys.put("processors", Runtime.getRuntime().availableProcessors());
                sys.put("freeMemoryMB", Runtime.getRuntime().freeMemory() / (1024 * 1024));
                sys.put("totalMemoryMB", Runtime.getRuntime().totalMemory() / (1024 * 1024));
                sys.put("javaVersion", System.getProperty("java.version"));
                sys.put("javaVendor", System.getProperty("java.vendor"));
                details.put("system", sys);

                // Project detection
                List<String> projectMarkers = new ArrayList<>();
                if (Files.exists(targetPath.resolve("pom.xml"))) projectMarkers.add("Maven");
                if (Files.exists(targetPath.resolve("build.gradle")) || Files.exists(targetPath.resolve("build.gradle.kts"))) projectMarkers.add("Gradle");
                if (Files.exists(targetPath.resolve("package.json"))) projectMarkers.add("Node/NPM");
                if (Files.exists(targetPath.resolve("Cargo.toml"))) projectMarkers.add("Rust/Cargo");
                if (Files.exists(targetPath.resolve("go.mod"))) projectMarkers.add("Go");
                if (Files.exists(targetPath.resolve("pyproject.toml")) || Files.exists(targetPath.resolve("requirements.txt"))) projectMarkers.add("Python");
                details.put("projectTypes", projectMarkers);

                // File counts
                long fileCount = 0;
                long totalSize = 0;
                try (Stream<Path> walk = Files.walk(targetPath, depth)) {
                    List<Path> files = walk.filter(Files::isRegularFile).toList();
                    fileCount = files.size();
                    totalSize = files.stream().mapToLong(p -> {
                        try { return Files.size(p); } catch (IOException e) { return 0; }
                    }).sum();
                } catch (IOException ignored) {}

                details.put("scannedFiles", fileCount);
                details.put("totalSizeBytes", totalSize);

                return new WorkspaceInfoResult(true, "Workspace info collected successfully", details);

            } catch (Exception e) {
                return new WorkspaceInfoResult(false, "Error: " + e.getMessage(), null);
            }
        });
    }
}
