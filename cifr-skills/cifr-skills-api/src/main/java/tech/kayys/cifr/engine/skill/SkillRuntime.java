package tech.kayys.cifr.engine.skill;

import io.smallrye.mutiny.Uni;

public interface SkillRuntime {
    Uni<SkillSession> start(SkillDefinition skill, String taskId);
    Uni<SkillDecision> decide(SkillSession session);
    Uni<SkillSession> advance(SkillSession session, SkillDecision decision);
}
