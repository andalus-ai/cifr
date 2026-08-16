package tech.kayys.aljabr.engine.skill.impl;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.skill.*;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class CodeReviewSkill implements CodingSkill {

    private final SkillContract contract;

    public CodeReviewSkill() {
        this.contract = new SkillContract(
            SkillDescriptor.builder()
                .id("code.review")
                .name("Code Review")
                .description("Package diffs, affected symbols, and invariants for model-assisted code review")
                .category(SkillCategory.VERIFICATION)
                .reasoningMode(ReasoningMode.REQUIRED)
                .mutatesWorkspace(false)
                .inputSchema(Map.of(
                    "diff", "string (git diff or patch content)",
                    "changedFiles", "array of changed file paths",
                    "invariants", "array of critical system invariants to verify"
                ))
                .outputSchema(Map.of(
                    "reviewContext", "object containing diff, affected files, and verification checklists",
                    "requiresModel", "boolean"
                ))
                .build(),
            List.of(),
            List.of()
        );
    }

    @Override
    public SkillContract contract() {
        return contract;
    }

    @Override
    public Uni<SkillResult> execute(SkillContext context) {
        String diff = (String) context.parameters().get("diff");
        Object changedFilesObj = context.parameters().get("changedFiles");
        Object invariantsObj = context.parameters().get("invariants");

        List<String> files = changedFilesObj instanceof List<?> list ?
            list.stream().map(Object::toString).toList() : List.of();
        List<String> invariants = invariantsObj instanceof List<?> list ?
            list.stream().map(Object::toString).toList() : List.of();

        Map<String, Object> reviewContext = Map.of(
            "diff", diff != null ? diff : "",
            "changedFiles", files,
            "invariants", invariants,
            "checklists", List.of("correctness", "regressionRisk", "missingTests", "unnecessaryChanges")
        );

        return Uni.createFrom().item(SkillResult.success(
            String.format("Prepared review context for %d changed files", files.size()),
            Map.of("reviewContext", reviewContext, "requiresModel", true),
            files
        ));
    }
}
