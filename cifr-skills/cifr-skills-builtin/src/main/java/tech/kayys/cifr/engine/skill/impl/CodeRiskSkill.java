package tech.kayys.cifr.engine.skill.impl;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.cifr.engine.index.impact.ImpactAnalyzer;
import tech.kayys.cifr.engine.index.impact.ImpactReport;
import tech.kayys.cifr.engine.patch.ChangeRisk;
import tech.kayys.cifr.engine.patch.ChangeRiskAnalyzer;
import tech.kayys.cifr.engine.patch.RiskLevel;
import tech.kayys.cifr.engine.repo.ChangeRecord;
import tech.kayys.cifr.engine.repo.ChangeSet;
import tech.kayys.cifr.engine.repo.ChangeType;
import tech.kayys.cifr.engine.skill.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class CodeRiskSkill implements CodingSkill {

    private static final SkillId ID = SkillId.of("code.risk");

    private static final SkillMetadata METADATA = new SkillMetadata(
            "Code Risk",
            "Computes deterministic mutation risk from changed files and repository impact",
            SkillCategory.VERIFICATION,
            RiskLevel.MEDIUM,
            ReasoningMode.NONE,
            false,
            Set.of("repo.impact", "repo.risk", "policy.authorization")
    );

    @Inject
    ChangeRiskAnalyzer riskAnalyzer;

    @Inject
    ImpactAnalyzer impactAnalyzer;

    @Override
    public SkillId id() {
        return ID;
    }

    @Override
    public SkillMetadata metadata() {
        return METADATA;
    }

    @Override
    public Uni<SkillResult> execute(SkillContext context) {
        List<String> paths = extractPaths(context.parameters().get("changedPaths"));
        ChangeSet changes = new ChangeSet(paths.stream()
                .map(path -> new ChangeRecord(
                        path, ChangeType.MODIFIED, null, null, "risk-analysis", "risk-analysis"))
                .toList());

        return impactAnalyzer.analyze(changes)
                .map(impact -> {
                    ChangeRisk risk = riskAnalyzer.analyze(changes, impact);
                    return SkillResult.success(
                            "Change risk assessed as " + risk.level(),
                            Map.of(
                                    "riskLevel", risk.level().name(),
                                    "reasons", risk.reasons(),
                                    "changedPaths", paths,
                                    "affectedFiles", impact.affectedFiles().size(),
                                    "affectedSymbols", impact.affectedSymbols().size(),
                                    "likelyTests", impact.likelyTests().stream().map(java.nio.file.Path::toString).toList()
                            ));
                })
                .onFailure().recoverWithItem(error ->
                        SkillResult.failure("Risk analysis failed: " + error.getMessage()));
    }

    private List<String> extractPaths(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(v -> v != null).map(Object::toString).filter(s -> !s.isBlank()).toList();
        }
        if (value instanceof String s && !s.isBlank()) {
            return List.of(s);
        }
        return List.of();
    }
}
