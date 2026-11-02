package tech.kayys.cifr.engine.skill.impl;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.patch.RiskLevel;
import tech.kayys.cifr.engine.skill.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class CodeEditSkill implements CodingSkill {

    private static final SkillId ID = SkillId.of("code.edit");
    private static final SkillMetadata METADATA = new SkillMetadata(
        "Code Edit",
        "Generates structured EditProposals for safe validation and mutation engine execution",
        SkillCategory.MODIFICATION,
        RiskLevel.MEDIUM,
        ReasoningMode.REQUIRED,
        true,
        Set.of("model.edit", "repo.patch")
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
            String instruction = (String) context.parameters().getOrDefault("instruction", "");
            return SkillResult.success(
                "Edit proposal generated for: " + instruction,
                Map.of("instruction", instruction, "edits", List.of())
            );
        });
    }
}
