package tech.kayys.cifr.engine.skill.impl;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.patch.RiskLevel;
import tech.kayys.cifr.engine.skill.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class CodePlanSkill implements CodingSkill {

    private static final SkillId ID = SkillId.of("code.plan");
    private static final SkillMetadata METADATA = new SkillMetadata(
        "Code Plan",
        "Decomposes high-level coding goals into minimal, bounded implementation steps",
        SkillCategory.PLANNING,
        RiskLevel.LOW,
        ReasoningMode.REQUIRED,
        false,
        Set.of("model.plan")
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
            String goal = (String) context.parameters().getOrDefault("goal", "");
            return SkillResult.success(
                "Plan created for: " + goal,
                Map.of("goal", goal, "steps", List.of("Inspect requirements", "Propose mutation", "Verify"))
            );
        });
    }
}
