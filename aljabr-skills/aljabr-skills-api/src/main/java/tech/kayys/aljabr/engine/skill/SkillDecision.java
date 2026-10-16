package tech.kayys.aljabr.engine.skill;

public sealed interface SkillDecision permits
    SkillDecision.RequestCapability,
    SkillDecision.ProposeChange,
    SkillDecision.Verify,
    SkillDecision.Finish,
    SkillDecision.Blocked {

    record RequestCapability(String capabilityId, java.util.Map<String, Object> arguments) implements SkillDecision {}
    record ProposeChange(String target, String description) implements SkillDecision {}
    record Verify(String requirementId) implements SkillDecision {}
    record Finish(String summary) implements SkillDecision {}
    record Blocked(String reason) implements SkillDecision {}
}
