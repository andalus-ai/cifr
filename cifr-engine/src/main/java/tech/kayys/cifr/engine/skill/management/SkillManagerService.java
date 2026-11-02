package tech.kayys.cifr.engine.skill.management;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.cifr.engine.skill.*;
import tech.kayys.cifr.engine.skill.impl.MarkdownSkill;
import tech.kayys.cifr.engine.skill.loader.AndalusSkillDirectoryLoader;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for runtime skill management: CRUD operations, hot reloading, and soft-delete/trash.
 */
@ApplicationScoped
public class SkillManagerService {

    private static final Logger LOG = Logger.getLogger(SkillManagerService.class);
    private static final String TRASH_DIR_NAME = ".trash";

    @Inject
    public SkillRegistry skillRegistry;

    @Inject
    public AndalusSkillDirectoryLoader directoryLoader;

    public record SkillSummary(
            String id,
            String name,
            String description,
            String category,
            String reasoningMode,
            boolean mutatesWorkspace,
            List<String> allowedTools,
            String sourcePath,
            boolean inTrash
    ) {}

    public record SkillDetailDto(
            SkillSummary summary,
            String instructions,
            Map<String, String> references,
            List<String> scripts,
            String rawMarkdown
    ) {}

    public record SkillInputDto(
            String id,
            String name,
            String description,
            String category,
            String reasoningMode,
            boolean mutatesWorkspace,
            List<String> allowedTools,
            String instructions,
            Map<String, String> references,
            Map<String, String> scripts
    ) {}

    public record SkillOperationResult(
            boolean success,
            String skillId,
            String message,
            String path,
            boolean movedToTrash
    ) {}

    /**
     * Lists active skills filtered by category, search query, and optionally trash.
     */
    public List<SkillSummary> listSkills(String category, String query, boolean includeTrash) {
        List<SkillDescriptor> descriptors = skillRegistry.listDescriptors();
        List<SkillSummary> results = new ArrayList<>();

        for (SkillDescriptor desc : descriptors) {
            if (category != null && !category.isBlank() &&
                    !desc.category().name().equalsIgnoreCase(category.strip())) {
                continue;
            }
            if (query != null && !query.isBlank()) {
                String q = query.toLowerCase();
                boolean match = desc.id().toLowerCase().contains(q) ||
                        desc.name().toLowerCase().contains(q) ||
                        desc.description().toLowerCase().contains(q);
                if (!match) continue;
            }

            // Find underlying skill if markdown
            Optional<CodingSkill> codingSkill = skillRegistry.find(desc.id());
            String path = "";
            if (codingSkill.isPresent() && codingSkill.get() instanceof MarkdownSkill ms && ms.skillDirectory() != null) {
                path = ms.skillDirectory().toAbsolutePath().toString();
            }

            results.add(new SkillSummary(
                    desc.id(),
                    desc.name(),
                    desc.description(),
                    desc.category().name(),
                    desc.reasoningMode().name(),
                    desc.mutatesWorkspace(),
                    List.copyOf(desc.requiredCapabilities()),
                    path,
                    false
            ));
        }

        if (includeTrash) {
            results.addAll(listTrash());
        }

        results.sort(Comparator.comparing(SkillSummary::name, String.CASE_INSENSITIVE_ORDER));
        return results;
    }

    /**
     * Gets full detail of a skill by ID.
     */
    public Optional<SkillDetailDto> getSkill(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return Optional.empty();
        }

        Optional<CodingSkill> skillOpt = skillRegistry.find(skillId);
        if (skillOpt.isEmpty()) {
            // Check trash
            return getTrashedSkill(skillId);
        }

        CodingSkill skill = skillOpt.get();
        SkillDescriptor desc = skill.descriptor();

        String instructions = "";
        Map<String, String> references = Map.of();
        List<String> scripts = List.of();
        String rawMarkdown = "";
        String sourcePath = "";

        if (skill instanceof MarkdownSkill ms) {
            instructions = ms.instructions();
            references = ms.references();
            scripts = ms.scriptPaths();
            if (ms.skillDirectory() != null) {
                sourcePath = ms.skillDirectory().toAbsolutePath().toString();
                Path mdFile = ms.skillDirectory().resolve("SKILL.md");
                if (Files.isRegularFile(mdFile)) {
                    try {
                        rawMarkdown = Files.readString(mdFile);
                    } catch (IOException ignored) {}
                }
            }
        }

        SkillSummary summary = new SkillSummary(
                desc.id(),
                desc.name(),
                desc.description(),
                desc.category().name(),
                desc.reasoningMode().name(),
                desc.mutatesWorkspace(),
                List.copyOf(desc.requiredCapabilities()),
                sourcePath,
                false
        );

        return Optional.of(new SkillDetailDto(summary, instructions, references, scripts, rawMarkdown));
    }

    /**
     * Creates or updates a skill in ~/.andalus/skills/<id>/SKILL.md and hot-reloads it in registry.
     */
    public SkillOperationResult saveSkill(SkillInputDto input) {
        if (input.id() == null || input.id().isBlank()) {
            return new SkillOperationResult(false, null, "Skill id is required", null, false);
        }

        String skillId = sanitizeId(input.id());
        Path baseSkillsDir = Paths.get(System.getProperty("user.home"), ".andalus", "skills");
        Path skillDir = baseSkillsDir.resolve(skillId);

        try {
            Files.createDirectories(skillDir);

            // Construct YAML Frontmatter + Markdown
            StringBuilder sb = new StringBuilder();
            sb.append("---\n");
            sb.append("id: ").append(skillId).append("\n");
            sb.append("name: ").append(input.name() != null && !input.name().isBlank() ? input.name() : skillId).append("\n");
            if (input.description() != null && !input.description().isBlank()) {
                sb.append("description: \"").append(input.description().replace("\"", "\\\"")).append("\"\n");
            }
            if (input.category() != null && !input.category().isBlank()) {
                sb.append("category: ").append(input.category().toUpperCase()).append("\n");
            }
            if (input.reasoningMode() != null && !input.reasoningMode().isBlank()) {
                sb.append("reasoningMode: ").append(input.reasoningMode().toUpperCase()).append("\n");
            }
            sb.append("mutatesWorkspace: ").append(input.mutatesWorkspace()).append("\n");
            if (input.allowedTools() != null && !input.allowedTools().isEmpty()) {
                sb.append("allowed-tools: [").append(String.join(", ", input.allowedTools())).append("]\n");
            }
            sb.append("---\n\n");
            sb.append(input.instructions() != null ? input.instructions() : "# " + input.name() + "\n\nNo instructions provided.\n");

            Files.writeString(skillDir.resolve("SKILL.md"), sb.toString());

            // Write references if provided
            if (input.references() != null && !input.references().isEmpty()) {
                Path refDir = skillDir.resolve("references");
                Files.createDirectories(refDir);
                for (Map.Entry<String, String> ref : input.references().entrySet()) {
                    Files.writeString(refDir.resolve(ref.getKey()), ref.getValue());
                }
            }

            // Write scripts if provided
            if (input.scripts() != null && !input.scripts().isEmpty()) {
                Path scrDir = skillDir.resolve("scripts");
                Files.createDirectories(scrDir);
                for (Map.Entry<String, String> scr : input.scripts().entrySet()) {
                    Files.writeString(scrDir.resolve(scr.getKey()), scr.getValue());
                }
            }

            // Load and hot-register into SkillRegistry
            Optional<MarkdownSkill> loaded = directoryLoader.loadSkillFromPath(skillDir);
            if (loaded.isPresent()) {
                skillRegistry.register(loaded.get());
                LOG.infof("Skill '%s' successfully saved and hot-registered at %s", skillId, skillDir);
                return new SkillOperationResult(true, skillId, "Skill saved and registered successfully", skillDir.toString(), false);
            } else {
                return new SkillOperationResult(true, skillId, "Skill files written to disk", skillDir.toString(), false);
            }

        } catch (IOException e) {
            LOG.errorf(e, "Failed to save skill '%s'", skillId);
            return new SkillOperationResult(false, skillId, "Failed to save skill: " + e.getMessage(), null, false);
        }
    }

    /**
     * Soft-deletes a skill by moving its folder to ~/.andalus/skills/.trash/<id> and unregisters it.
     */
    public SkillOperationResult deleteSkill(String skillId, boolean hardDelete) {
        if (skillId == null || skillId.isBlank()) {
            return new SkillOperationResult(false, null, "Skill id is required", null, false);
        }

        String id = sanitizeId(skillId);
        Path targetDir = findSkillDirectory(id);

        if (targetDir == null || !Files.exists(targetDir)) {
            // If only registered in memory, just unregister
            boolean removed = skillRegistry.unregister(id);
            if (removed) {
                return new SkillOperationResult(true, id, "Skill unregistered from memory", null, false);
            }
            return new SkillOperationResult(false, id, "Skill not found: " + id, null, false);
        }

        try {
            if (hardDelete) {
                deleteRecursively(targetDir);
                skillRegistry.unregister(id);
                LOG.infof("Skill '%s' hard-deleted from %s", id, targetDir);
                return new SkillOperationResult(true, id, "Skill permanently deleted", targetDir.toString(), false);
            } else {
                // Soft delete: move to trash
                Path trashDir = getTrashDirectory();
                Files.createDirectories(trashDir);

                Path destination = trashDir.resolve(id);
                if (Files.exists(destination)) {
                    deleteRecursively(destination);
                }

                Files.move(targetDir, destination, StandardCopyOption.REPLACE_EXISTING);
                skillRegistry.unregister(id);
                LOG.infof("Skill '%s' moved to trash at %s", id, destination);
                return new SkillOperationResult(true, id, "Skill moved to trash", destination.toString(), true);
            }
        } catch (IOException e) {
            LOG.errorf(e, "Failed to delete/trash skill '%s'", id);
            return new SkillOperationResult(false, id, "Error deleting skill: " + e.getMessage(), null, false);
        }
    }

    /**
     * Restores a soft-deleted skill from ~/.andalus/skills/.trash/<id> back to ~/.andalus/skills/<id>.
     */
    public SkillOperationResult restoreSkill(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return new SkillOperationResult(false, null, "Skill id is required", null, false);
        }

        String id = sanitizeId(skillId);
        Path trashDir = getTrashDirectory();
        Path trashedSkill = trashDir.resolve(id);

        if (!Files.exists(trashedSkill)) {
            return new SkillOperationResult(false, id, "Skill not found in trash: " + id, null, false);
        }

        Path activeSkillsDir = Paths.get(System.getProperty("user.home"), ".andalus", "skills");
        Path restoredDir = activeSkillsDir.resolve(id);

        try {
            Files.createDirectories(activeSkillsDir);
            Files.move(trashedSkill, restoredDir, StandardCopyOption.REPLACE_EXISTING);

            // Re-register into active registry
            Optional<MarkdownSkill> loaded = directoryLoader.loadSkillFromPath(restoredDir);
            if (loaded.isPresent()) {
                skillRegistry.register(loaded.get());
                LOG.infof("Skill '%s' restored from trash and re-registered at %s", id, restoredDir);
                return new SkillOperationResult(true, id, "Skill restored and registered", restoredDir.toString(), false);
            } else {
                return new SkillOperationResult(true, id, "Skill restored to directory", restoredDir.toString(), false);
            }
        } catch (IOException e) {
            LOG.errorf(e, "Failed to restore skill '%s'", id);
            return new SkillOperationResult(false, id, "Failed to restore skill: " + e.getMessage(), null, false);
        }
    }

    /**
     * Lists all skills currently residing in the trash folder.
     */
    public List<SkillSummary> listTrash() {
        Path trashDir = getTrashDirectory();
        if (!Files.isDirectory(trashDir)) {
            return List.of();
        }

        List<SkillSummary> trashed = new ArrayList<>();
        try (Stream<Path> stream = Files.list(trashDir)) {
            for (Path entry : stream.toList()) {
                try {
                    Optional<MarkdownSkill> skillOpt = directoryLoader.loadSkillFromPath(entry);
                    if (skillOpt.isPresent()) {
                        MarkdownSkill s = skillOpt.get();
                        SkillDescriptor d = s.descriptor();
                        trashed.add(new SkillSummary(
                                d.id(),
                                d.name(),
                                d.description(),
                                d.category().name(),
                                d.reasoningMode().name(),
                                d.mutatesWorkspace(),
                                List.copyOf(d.requiredCapabilities()),
                                entry.toAbsolutePath().toString(),
                                true
                        ));
                    } else {
                        trashed.add(new SkillSummary(
                                entry.getFileName().toString(),
                                entry.getFileName().toString(),
                                "Trashed skill item",
                                "CUSTOM",
                                "NONE",
                                false,
                                List.of(),
                                entry.toAbsolutePath().toString(),
                                true
                        ));
                    }
                } catch (Exception ignored) {}
            }
        } catch (IOException ignored) {}

        return trashed;
    }

    /**
     * Hot-reloads all skills from disk.
     */
    public int reloadSkills() {
        int count = directoryLoader.loadAll();
        LOG.infof("Hot-reloaded %d skills from disk", count);
        return count;
    }

    private Optional<SkillDetailDto> getTrashedSkill(String skillId) {
        Path trashDir = getTrashDirectory();
        Path trashedSkillPath = trashDir.resolve(sanitizeId(skillId));
        if (!Files.exists(trashedSkillPath)) {
            return Optional.empty();
        }

        try {
            Optional<MarkdownSkill> opt = directoryLoader.loadSkillFromPath(trashedSkillPath);
            if (opt.isPresent()) {
                MarkdownSkill s = opt.get();
                SkillDescriptor d = s.descriptor();
                SkillSummary summary = new SkillSummary(
                        d.id(), d.name(), d.description(), d.category().name(),
                        d.reasoningMode().name(), d.mutatesWorkspace(),
                        List.copyOf(d.requiredCapabilities()), trashedSkillPath.toString(), true);
                return Optional.of(new SkillDetailDto(summary, s.instructions(), s.references(), s.scriptPaths(), ""));
            }
        } catch (Exception ignored) {}

        return Optional.empty();
    }

    private Path getTrashDirectory() {
        return Paths.get(System.getProperty("user.home"), ".andalus", "skills", TRASH_DIR_NAME);
    }

    private Path findSkillDirectory(String skillId) {
        List<Path> searchDirs = directoryLoader.resolveSearchDirs();
        for (Path base : searchDirs) {
            Path candidate = base.resolve(skillId);
            if (Files.exists(candidate)) {
                return candidate;
            }
            Path candidateMd = base.resolve(skillId + ".md");
            if (Files.exists(candidateMd)) {
                return candidateMd;
            }
        }
        return null;
    }

    private String sanitizeId(String id) {
        return id.strip().replaceAll("[^a-zA-Z0-9._-]", "-").toLowerCase();
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
