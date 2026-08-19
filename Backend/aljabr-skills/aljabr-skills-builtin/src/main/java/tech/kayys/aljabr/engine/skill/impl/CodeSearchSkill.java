package tech.kayys.aljabr.engine.skill.impl;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.aljabr.engine.patch.RiskLevel;
import tech.kayys.aljabr.engine.skill.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class CodeSearchSkill implements CodingSkill {

    private static final SkillId ID = SkillId.of("code.search");
    private static final SkillMetadata METADATA = new SkillMetadata(
        "Code Search",
        "Deterministic symbol, reference, and text search across the codebase",
        SkillCategory.DISCOVERY,
        RiskLevel.LOW,
        ReasoningMode.NONE,
        false,
        Set.of("repo.search", "repo.symbol")
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
            String query = (String) context.parameters().getOrDefault("query", "");
            return SkillResult.success(
                "Search completed for query: " + query,
                Map.of("query", query, "matches", List.of())
            );
        });
    }
}
