package tech.kayys.cifr.engine.skill.runtime;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.skill.SkillSelectionReason;

import java.util.Map;

@ApplicationScoped
public class DefaultSkillSelector {

    private static final Map<String, String> KEYWORD_MAP = Map.of(
        "fix", "debugging",
        "bug", "debugging",
        "error", "debugging",
        "refactor", "refactoring",
        "test", "testing",
        "review", "code-review",
        "document", "documentation"
    );

    public Uni<SelectedSkill> select(String goal) {
        String lower = goal != null ? goal.toLowerCase() : "";
        for (Map.Entry<String, String> entry : KEYWORD_MAP.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return Uni.createFrom().item(new SelectedSkill(entry.getValue(), SkillSelectionReason.KEYWORD_MATCH, 0.9));
            }
        }
        return Uni.createFrom().item(new SelectedSkill("coding", SkillSelectionReason.PHASE_INFERRED, 0.5));
    }

    public record SelectedSkill(String skillId, SkillSelectionReason reason, double confidence) {}
}
