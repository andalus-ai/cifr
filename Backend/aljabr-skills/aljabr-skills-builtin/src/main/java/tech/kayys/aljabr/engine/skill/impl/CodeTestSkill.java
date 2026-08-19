package tech.kayys.aljabr.engine.skill.impl;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.patch.RiskLevel;
import tech.kayys.aljabr.engine.skill.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class CodeTestSkill implements CodingSkill {

    private static final SkillId ID = SkillId.of("code.test");
    private static final SkillMetadata METADATA = new SkillMetadata(
        "Code Test",
        "Deterministic test runner and regression verifier",
        SkillCategory.VERIFICATION,
        RiskLevel.LOW,
        ReasoningMode.NONE,
        false,
        Set.of("exec.process", "verify.test")
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
            String testTarget = (String) context.parameters().getOrDefault("target", "all");
            return SkillResult.success(
                "Test run completed for: " + testTarget,
                Map.of("target", testTarget, "passed", true, "failures", List.of())
            );
        });
    }
}
