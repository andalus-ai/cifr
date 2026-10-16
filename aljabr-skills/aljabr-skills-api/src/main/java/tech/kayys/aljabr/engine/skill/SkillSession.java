package tech.kayys.aljabr.engine.skill;

public record SkillSession(
    SkillId skillId,
    SkillVersion version,
    String taskId,
    String phase,
    SkillState state
) {
    public static SkillSession start(SkillId skillId, String taskId, String entryPhase) {
        return new SkillSession(skillId, SkillVersion.v1(), taskId, entryPhase, SkillState.initial());
    }

    public SkillSession withPhase(String newPhase) {
        return new SkillSession(skillId, version, taskId, newPhase, state);
    }
}
