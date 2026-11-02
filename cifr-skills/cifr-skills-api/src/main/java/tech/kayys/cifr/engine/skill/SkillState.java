package tech.kayys.cifr.engine.skill;

import java.util.List;

public record SkillState(
    List<String> completedMilestones,
    String activeHypothesis,
    String pendingAction
) {
    public SkillState {
        completedMilestones = completedMilestones != null ? List.copyOf(completedMilestones) : List.of();
    }

    public static SkillState initial() {
        return new SkillState(List.of(), null, null);
    }
}
