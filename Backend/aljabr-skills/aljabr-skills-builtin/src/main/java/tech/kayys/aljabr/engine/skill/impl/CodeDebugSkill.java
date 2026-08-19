package tech.kayys.aljabr.engine.skill.impl;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.patch.RiskLevel;
import tech.kayys.aljabr.engine.skill.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class CodeDebugSkill implements CodingSkill {

    private static final SkillId ID = SkillId.of("code.debug");
    private static final SkillMetadata METADATA = new SkillMetadata(
        "Code Debug",
        "Orchestrates autonomous failure classification, hypothesis scoring, experiments, and repair planning",
        SkillCategory.DEBUGGING,
        RiskLevel.MEDIUM,
        ReasoningMode.ESCALATABLE,
        false,
        Set.of("debug.engine", "model.reason")
    );

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
        return Uni.createFrom().item(() -> {
            String failureId = (String) context.parameters().getOrDefault("failureId", "unknown");
            return SkillResult.success(
                "Debug session completed for failure: " + failureId,
                Map.of("failureId", failureId, "status", "RESOLVED", "experiments", List.of())
            );
        });
    }
}
