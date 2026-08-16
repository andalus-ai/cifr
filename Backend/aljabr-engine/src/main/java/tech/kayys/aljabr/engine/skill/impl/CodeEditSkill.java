package tech.kayys.aljabr.engine.skill.impl;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.aljabr.engine.patch.mutation.*;
import tech.kayys.aljabr.engine.skill.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class CodeEditSkill implements CodingSkill {

    private final SkillContract contract;

    @Inject
    MutationEngine mutationEngine;

    public CodeEditSkill() {
        this.contract = new SkillContract(
            SkillDescriptor.builder()
                .id("code.edit")
                .name("Code Edit")
                .description("Safe workspace mutation and file editing through transactional EditProposal and MutationEngine")
                .category(SkillCategory.MUTATION)
                .reasoningMode(ReasoningMode.REQUIRED)
                .mutatesWorkspace(true)
                .inputSchema(Map.of(
                    "file", "string (target file path to edit)",
                    "expectedText", "string (text to be replaced)",
                    "replacementText", "string (new text)",
                    "objective", "string (purpose of edit)"
                ))
                .outputSchema(Map.of(
                    "mutationId", "string",
                    "modifiedFiles", "array of modified file paths"
                ))
                .build(),
            List.of(),
            List.of()
        );
    }

    public CodeEditSkill(MutationEngine mutationEngine) {
        this();
        this.mutationEngine = mutationEngine;
    }

    @Override
    public SkillContract contract() {
        return contract;
    }

    @Override
    public Uni<SkillResult> execute(SkillContext context) {
        String filePathStr = (String) context.parameters().get("file");
        if (filePathStr == null || filePathStr.isBlank()) {
            return Uni.createFrom().item(SkillResult.failure("Missing required parameter: 'file'"));
        }

        String expectedText = (String) context.parameters().get("expectedText");
        String replacementText = (String) context.parameters().get("replacementText");
        if (replacementText == null) {
            return Uni.createFrom().item(SkillResult.failure("Missing required parameter: 'replacementText'"));
        }

        String objective = (String) context.parameters().getOrDefault("objective", "Edit " + filePathStr);
        Path targetPath = Path.of(filePathStr);

        TextEdit textEdit = new TextEdit(expectedText != null ? expectedText : "", replacementText);
        FileEdit fileEdit = new FileEdit(targetPath, List.of(textEdit));
        EditProposal proposal = new EditProposal(objective, List.of(fileEdit), List.of(), List.of(filePathStr));

        Path root = context.workspaceRoot() != null ? context.workspaceRoot() : Path.of(".");

        return mutationEngine.apply(proposal, root)
            .map(mutationResult -> {
                List<String> modified = mutationResult.changes().changes().stream()
                    .map(r -> r.path())
                    .collect(Collectors.toList());

                return SkillResult.success(
                    String.format("Successfully applied mutation %s to %d files", mutationResult.id().value(), modified.size()),
                    Map.of(
                        "mutationId", mutationResult.id().value(),
                        "modifiedFiles", modified
                    ),
                    mutationResult.evidenceIds()
                );
            });
    }
}
