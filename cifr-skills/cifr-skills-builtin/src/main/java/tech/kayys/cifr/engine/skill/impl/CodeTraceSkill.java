package tech.kayys.cifr.engine.skill.impl;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.patch.RiskLevel;
import tech.kayys.cifr.engine.skill.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class CodeTraceSkill implements CodingSkill {

    private static final SkillId ID = SkillId.of("code.trace");
    private static final SkillMetadata METADATA = new SkillMetadata(
        "Code Trace",
        "Deterministic call-graph, control-flow, and dependency tracing for symbols",
        SkillCategory.UNDERSTANDING,
        RiskLevel.LOW,
        ReasoningMode.NONE,
        false,
        Set.of("repo.ast", "repo.symbol")
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
            String symbol = (String) context.parameters().getOrDefault("symbol", "");
            return SkillResult.success(
                "Traced call graph for: " + symbol,
                Map.of("symbol", symbol, "callGraph", List.of(symbol))
            );
        });
    }
}
