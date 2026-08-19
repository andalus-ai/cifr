package tech.kayys.aljabr.engine.skill.impl;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.patch.RiskLevel;
import tech.kayys.aljabr.engine.skill.*;

import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class CodeInspectSkill implements CodingSkill {

    private static final SkillId ID = SkillId.of("code.inspect");
    private static final SkillMetadata METADATA = new SkillMetadata(
        "Code Inspect",
        "Inspect source code, symbols, and dependencies relevant to a task without mutating files",
        SkillCategory.DISCOVERY,
        RiskLevel.LOW,
        ReasoningMode.OPTIONAL,
        false,
        Set.of("repo.read", "repo.symbol", "repo.search")
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
            String target = (String) context.parameters().getOrDefault("target", "");
            return SkillResult.success(
                "Inspected target: " + target,
                Map.of("target", target, "inspectionStatus", "COMPLETED")
            );
        });
    }
}
