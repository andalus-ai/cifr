package tech.kayys.aljabr.engine.skill.loader;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import tech.kayys.aljabr.engine.skill.*;
import tech.kayys.aljabr.engine.skill.impl.MarkdownSkill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Loads skills from the default Andalus repository directory ({@code ~/.andalus/skills})
 * and workspace-local directory ({@code .andalus/skills}), and registers them into
 * the active {@link SkillRegistry}.
 *
 * <p>Supports the agentskills.io specification ({@code SKILL.md} with YAML frontmatter).
 */
@ApplicationScoped
public class AndalusSkillDirectoryLoader {

    private static final Logger LOG = Logger.getLogger(AndalusSkillDirectoryLoader.class);
    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile(
            "^---\\s*\\n(.*?)\\n---\\s*\\n(.*)", Pattern.DOTALL);

    @Inject
    public SkillRegistry skillRegistry;

    void onStart(@Observes StartupEvent ev) {
        loadAll();
    }

    /**
     * Loads or reloads all skills from default and workspace paths.
     *
     * @return count of skills discovered and registered
     */
    public int loadAll() {
        List<Path> searchDirs = resolveSearchDirs();
        int loadedCount = 0;

        for (Path dir : searchDirs) {
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> entries = Files.list(dir)) {
                for (Path entry : entries.toList()) {
                    try {
                        Optional<MarkdownSkill> skill = loadSkillFromPath(entry);
                        if (skill.isPresent()) {
                            skillRegistry.register(skill.get());
                            loadedCount++;
                            LOG.debugf("Registered Andalus skill: %s (id=%s) from %s",
                                    skill.get().metadata().name(), skill.get().id().value(), entry);
                        }
                    } catch (Exception e) {
                        LOG.warnf("Failed to load skill from %s: %s", entry, e.getMessage());
                    }
                }
            } catch (IOException e) {
                LOG.warnf("Cannot list skills directory %s: %s", dir, e.getMessage());
            }
        }

        LOG.infof("Andalus skill repository loader finished: %d skills registered from %s",
                loadedCount, searchDirs);
        return loadedCount;
    }

    public List<Path> resolveSearchDirs() {
        List<Path> dirs = new ArrayList<>();
        // 1. User home global skills directory: ~/.andalus/skills
        Path userGlobal = Paths.get(System.getProperty("user.home"), ".andalus", "skills");
        if (Files.isDirectory(userGlobal)) {
            dirs.add(userGlobal);
        }

        // 2. Fallback to ~/.aljabr/skills
        Path aljabrGlobal = Paths.get(System.getProperty("user.home"), ".aljabr", "skills");
        if (Files.isDirectory(aljabrGlobal) && !aljabrGlobal.equals(userGlobal)) {
            dirs.add(aljabrGlobal);
        }

        // 3. Workspace local .andalus/skills
        Path workspaceLocal = Paths.get(".andalus", "skills");
        if (Files.isDirectory(workspaceLocal)) {
            dirs.add(workspaceLocal);
        }

        return dirs;
    }

    /**
     * Attempts to parse a skill from a directory (containing SKILL.md) or standalone .md file.
     */
    public Optional<MarkdownSkill> loadSkillFromPath(Path path) throws IOException {
        Path skillMdFile = null;
        Path skillDir = null;
        String idHint = path.getFileName().toString();

        if (Files.isDirectory(path)) {
            skillDir = path;
            Path skillMd = path.resolve("SKILL.md");
            Path lowerMd = path.resolve("skill.md");
            Path readmeMd = path.resolve("README.md");

            if (Files.isRegularFile(skillMd)) {
                skillMdFile = skillMd;
            } else if (Files.isRegularFile(lowerMd)) {
                skillMdFile = lowerMd;
            } else if (Files.isRegularFile(readmeMd)) {
                skillMdFile = readmeMd;
            } else {
                return Optional.empty();
            }
        } else if (path.toString().endsWith(".md") && !path.getFileName().toString().equalsIgnoreCase("README.md")) {
            skillMdFile = path;
            skillDir = path.getParent();
            idHint = idHint.replace(".md", "");
        } else {
            return Optional.empty();
        }

        String raw = Files.readString(skillMdFile);
        if (raw.isBlank()) return Optional.empty();

        Matcher matcher = FRONTMATTER_PATTERN.matcher(raw);
        String frontmatter = "";
        String instructions = raw;

        if (matcher.matches()) {
            frontmatter = matcher.group(1);
            instructions = matcher.group(2).strip();
        }

        // Parse frontmatter properties line-by-line (simple parser, zero extra dependency)
        Map<String, String> fmMap = new LinkedHashMap<>();
        List<String> allowedTools = new ArrayList<>();
        if (!frontmatter.isBlank()) {
            for (String line : frontmatter.split("\\r?\\n")) {
                line = line.strip();
                if (line.startsWith("#") || line.isBlank()) continue;
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String key = line.substring(0, colon).strip();
                    String val = line.substring(colon + 1).strip();
                    if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                        val = val.substring(1, val.length() - 1);
                    } else if (val.startsWith("'") && val.endsWith("'") && val.length() >= 2) {
                        val = val.substring(1, val.length() - 1);
                    }
                    fmMap.put(key, val);
                }
            }
        }

        String id = fmMap.getOrDefault("id", idHint);
        String name = fmMap.getOrDefault("name", idHint);
        String description = fmMap.getOrDefault("description", "");
        String categoryStr = fmMap.getOrDefault("category", "DISCOVERY");
        SkillCategory category;
        try {
            category = SkillCategory.valueOf(categoryStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            category = SkillCategory.DISCOVERY;
        }

        String reasoningStr = fmMap.getOrDefault("reasoningMode", "NONE");
        ReasoningMode reasoningMode;
        try {
            reasoningMode = ReasoningMode.valueOf(reasoningStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            reasoningMode = ReasoningMode.NONE;
        }

        boolean mutatesWorkspace = Boolean.parseBoolean(fmMap.getOrDefault("mutatesWorkspace", "false"));

        // Scan references and scripts if directory exists
        Map<String, String> references = new HashMap<>();
        List<String> scriptPaths = new ArrayList<>();

        if (skillDir != null) {
            Path refDir = skillDir.resolve("references");
            if (Files.isDirectory(refDir)) {
                try (Stream<Path> refs = Files.list(refDir)) {
                    for (Path refFile : refs.filter(Files::isRegularFile).toList()) {
                        try {
                            references.put(refFile.getFileName().toString(), Files.readString(refFile));
                        } catch (IOException ignored) {}
                    }
                }
            }

            Path scriptsDir = skillDir.resolve("scripts");
            if (Files.isDirectory(scriptsDir)) {
                try (Stream<Path> scr = Files.list(scriptsDir)) {
                    scriptPaths = scr.filter(Files::isRegularFile)
                            .map(p -> p.getFileName().toString())
                            .sorted()
                            .toList();
                }
            }
        }

        return Optional.of(new MarkdownSkill(
                id,
                name,
                description,
                category,
                reasoningMode,
                mutatesWorkspace,
                instructions,
                skillDir,
                allowedTools,
                references,
                scriptPaths
        ));
    }
}
