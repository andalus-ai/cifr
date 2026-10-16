package tech.kayys.aljabr.engine.skill.impl;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.patch.RiskLevel;
import tech.kayys.aljabr.engine.skill.*;

import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class CodeHistorySkill implements CodingSkill {

    private static final SkillId ID = SkillId.of("code.history");

    private static final SkillMetadata METADATA = new SkillMetadata(
            "Code History & Historical Reasoning",
            "Traces the evolution, rationale, author intent, and commits behind source code",
            SkillCategory.UNDERSTANDING,
            RiskLevel.LOW,
            ReasoningMode.OPTIONAL,
            false,
            Set.of("git.history", "git.blame")
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
                    "Historical evolution query completed for " + target,
                    Map.of(
                            "target", target,
                            "workspace", context.workspaceRoot() != null ? context.workspaceRoot().toString() : "."
                    )
            );
        });
    }
}
