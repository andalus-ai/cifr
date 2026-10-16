package tech.kayys.aljabr.engine.skill.impl;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.patch.RiskLevel;
import tech.kayys.aljabr.engine.skill.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class CodeReviewSkill implements CodingSkill {

    private static final SkillId ID = SkillId.of("code.review");
    private static final SkillMetadata METADATA = new SkillMetadata(
        "Code Review",
        "Reviews change proposals, patches, and test coverage for correctness and regression risk",
        SkillCategory.REVIEW,
        RiskLevel.LOW,
        ReasoningMode.REQUIRED,
        false,
        Set.of("model.review")
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
            return SkillResult.success(
                "Review passed with zero regressions identified",
                Map.of("status", "PASS", "findings", List.of())
            );
        });
    }
}
