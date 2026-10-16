package tech.kayys.aljabr.engine;

import tech.kayys.andalus.agent.core.skills.loader.SkillExecutor;
import tech.kayys.andalus.agent.core.skills.adapters.ManifestSkillToolAdapter;
import tech.kayys.andalus.tools.spi.Tool;
import tech.kayys.andalus.tools.spi.ToolContext;
import tech.kayys.andalus.tools.spi.ToolResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AljabrSkillAdapter implements Tool {

    private final ManifestSkillToolAdapter adapter;

    public AljabrSkillAdapter(ManifestSkillToolAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public String id() {
        return adapter.getToolName();
    }

    @Override
    public String name() {
        return adapter.getToolName();
    }

    @Override
    public String description() {
        return adapter.getToolDescription();
    }

    @Override
    public Map<String, Object> inputSchema() {
        return adapter.getParameters();
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments, ToolContext context) {
        ManifestSkillToolAdapter.ToolExecutionResult result = adapter.execute(arguments);
        if (result.success()) {
            return ToolResult.success(result.output());
        } else {
            return ToolResult.error(result.error());
        }
    }

    public static List<Tool> discoverSkills(Path workspaceDir) {
        List<Tool> tools = new ArrayList<>();
        try {
            // Setup ~/.andalus/skills as the primary global skills location
            Path andalusHome = Path.of(System.getProperty("user.home"), ".andalus");
            Path globalSkills = andalusHome.resolve("skills");

            // Extract bundled skills if they do not exist
            extractDefaultSkills(globalSkills);

            if (java.nio.file.Files.exists(globalSkills)) {
                SkillExecutor executor = new SkillExecutor(globalSkills);
                Map<String, ManifestSkillToolAdapter> adapters = ManifestSkillToolAdapter
                        .createAdaptersForAllSkills(executor);
                tools.addAll(adapters.values().stream().map(AljabrSkillAdapter::new).collect(Collectors.toList()));
            }

            // Walk up the directory tree to find workspace skills
            Path current = workspaceDir.toAbsolutePath().normalize();
            while (current != null) {
                Path andalusSkills = current.resolve("andalus").resolve("skills").resolve("skills");
                if (java.nio.file.Files.exists(andalusSkills)) {
                    try {
                        SkillExecutor executor = new SkillExecutor(andalusSkills);
                        Map<String, ManifestSkillToolAdapter> adapters = ManifestSkillToolAdapter
                                .createAdaptersForAllSkills(executor);
                        tools.addAll(
                                adapters.values().stream().map(AljabrSkillAdapter::new).collect(Collectors.toList()));
                    } catch (Exception e) {
                        System.err.println("[AljabrSkillAdapter] Error in andalusSkills: " + e.getMessage());
                    }
                }

                Path agentSkills = current.resolve(".agents").resolve("skills");
                if (java.nio.file.Files.exists(agentSkills)) {
                    try {
                        SkillExecutor executor = new SkillExecutor(agentSkills);
                        Map<String, ManifestSkillToolAdapter> adapters = ManifestSkillToolAdapter
                                .createAdaptersForAllSkills(executor);
                        tools.addAll(
                                adapters.values().stream().map(AljabrSkillAdapter::new).collect(Collectors.toList()));
                    } catch (Exception e) {
                        System.err.println("[AljabrSkillAdapter] Error in agentSkills: " + e.getMessage());
                    }
                }

                // Break if we hit a likely root to avoid scanning entire filesystem uselessly
                if (java.nio.file.Files.exists(current.resolve(".git")) || current.getParent() == null) {
                    break;
                }
                current = current.getParent();
            }

        } catch (Exception e) {
            System.err.println("[AljabrSkillAdapter] Failed to discover skills: " + e.getMessage());
        }
        return tools;
    }

    private static void extractDefaultSkills(Path destDir) {
        if (java.nio.file.Files.exists(destDir)) {
            return;
        }
        try {
            java.nio.file.Files.createDirectories(destDir);
            java.net.URL resource = AljabrSkillAdapter.class.getResource("/default-skills");
            if (resource == null) {
                return;
            }
            if ("jar".equals(resource.getProtocol())) {
                try (java.nio.file.FileSystem fs = java.nio.file.FileSystems.newFileSystem(resource.toURI(),
                        java.util.Collections.emptyMap())) {
                    Path jarPath = fs.getPath("/default-skills");
                    java.nio.file.Files.walkFileTree(jarPath, new java.nio.file.SimpleFileVisitor<Path>() {
                        @Override
                        public java.nio.file.FileVisitResult preVisitDirectory(Path dir,
                                java.nio.file.attribute.BasicFileAttributes attrs) throws java.io.IOException {
                            Path targetDir = destDir.resolve(jarPath.relativize(dir).toString());
                            if (!java.nio.file.Files.exists(targetDir)) {
                                java.nio.file.Files.createDirectory(targetDir);
                            }
                            return java.nio.file.FileVisitResult.CONTINUE;
                        }

                        @Override
                        public java.nio.file.FileVisitResult visitFile(Path file,
                                java.nio.file.attribute.BasicFileAttributes attrs) throws java.io.IOException {
                            java.nio.file.Files.copy(file, destDir.resolve(jarPath.relativize(file).toString()),
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            return java.nio.file.FileVisitResult.CONTINUE;
                        }
                    });
                }
            } else if ("file".equals(resource.getProtocol())) {
                Path sourceDir = Path.of(resource.toURI());
                java.nio.file.Files.walkFileTree(sourceDir, new java.nio.file.SimpleFileVisitor<Path>() {
                    @Override
                    public java.nio.file.FileVisitResult preVisitDirectory(Path dir,
                            java.nio.file.attribute.BasicFileAttributes attrs) throws java.io.IOException {
                        Path target = destDir.resolve(sourceDir.relativize(dir).toString());
                        if (!java.nio.file.Files.exists(target)) {
                            java.nio.file.Files.createDirectory(target);
                        }
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }

                    @Override
                    public java.nio.file.FileVisitResult visitFile(Path file,
                            java.nio.file.attribute.BasicFileAttributes attrs) throws java.io.IOException {
                        java.nio.file.Files.copy(file, destDir.resolve(sourceDir.relativize(file).toString()),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("[AljabrSkillAdapter] Failed to extract default skills: " + e.getMessage());
        }
    }
}
