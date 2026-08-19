package tech.kayys.aljabr.engine.skill.impl;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.patch.RiskLevel;
import tech.kayys.aljabr.engine.skill.*;

import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class CodeExplainSkill implements CodingSkill {

    private static final SkillId ID = SkillId.of("code.explain");
    private static final SkillMetadata METADATA = new SkillMetadata(
        "Code Explain",
        "Synthesizes structured explanations and answers questions regarding code implementation",
        SkillCategory.UNDERSTANDING,
        RiskLevel.LOW,
        ReasoningMode.REQUIRED,
        false,
        Set.of("model.chat")
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
            String question = (String) context.parameters().getOrDefault("question", "");
            return SkillResult.success(
                "Explanation generated for: " + question,
                Map.of("question", question, "explanation", "Explanation details...")
            );
        });
    }
}
