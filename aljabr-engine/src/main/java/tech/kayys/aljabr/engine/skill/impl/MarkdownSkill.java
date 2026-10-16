package tech.kayys.aljabr.engine.skill.impl;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.patch.RiskLevel;
import tech.kayys.aljabr.engine.skill.*;

import java.nio.file.Path;
import java.util.*;

/**
 * A dynamic coding skill loaded from a {@code SKILL.md} file or skill folder.
 *
 * <p>Conforms to the agentskills.io specification (YAML frontmatter + Markdown instructions).
 */
public class MarkdownSkill implements CodingSkill {

    private final SkillId id;
    private final SkillMetadata metadata;
    private final String instructions;
    private final Path skillDirectory;
    private final List<String> allowedTools;
    private final Map<String, String> references;
    private final List<String> scriptPaths;

    public MarkdownSkill(
            String id,
            String name,
            String description,
            SkillCategory category,
            ReasoningMode reasoningMode,
            boolean mutatesWorkspace,
            String instructions,
            Path skillDirectory,
            List<String> allowedTools,
            Map<String, String> references,
            List<String> scriptPaths) {

        this.id = SkillId.of(id);
        this.metadata = new SkillMetadata(
                name != null ? name : id,
                description != null ? description : "",
                category != null ? category : SkillCategory.DISCOVERY,
                RiskLevel.LOW,
                reasoningMode != null ? reasoningMode : ReasoningMode.NONE,
                mutatesWorkspace,
                allowedTools != null ? new HashSet<>(allowedTools) : Set.of()
        );
        this.instructions = instructions != null ? instructions : "";
        this.skillDirectory = skillDirectory;
        this.allowedTools = allowedTools != null ? List.copyOf(allowedTools) : List.of();
        this.references = references != null ? Map.copyOf(references) : Map.of();
        this.scriptPaths = scriptPaths != null ? List.copyOf(scriptPaths) : List.of();
    }

    @Override
    public SkillId id() {
        return id;
    }

    @Override
    public SkillMetadata metadata() {
        return metadata;
    }

    public String instructions() {
        return instructions;
    }

    public Path skillDirectory() {
        return skillDirectory;
    }

    public List<String> allowedTools() {
        return allowedTools;
    }

    public Map<String, String> references() {
        return references;
    }

    public List<String> scriptPaths() {
        return scriptPaths;
    }

    @Override
    public Uni<SkillResult> execute(SkillContext context) {
        Map<String, Object> output = new HashMap<>();
        output.put("skillId", id.value());
        output.put("name", metadata.name());
        output.put("instructions", instructions);
        output.put("allowedTools", allowedTools);
        output.put("references", references);
        output.put("scripts", scriptPaths);

        return Uni.createFrom().item(SkillResult.success(
                "Loaded and applied instructions for skill: " + metadata.name(),
                output
        ));
    }
}
