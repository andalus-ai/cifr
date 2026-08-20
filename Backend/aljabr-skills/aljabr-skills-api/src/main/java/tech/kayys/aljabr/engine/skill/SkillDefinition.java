package tech.kayys.aljabr.engine.skill;

public record SkillDefinition(
    SkillId id,
    SkillVersion version,
    SkillObjective objective,
    SkillPhasePolicy phases,
    SkillCapabilityPolicy capabilities,
    SkillContextPolicy context,
    SkillReasoningPolicy reasoning,
    SkillMemoryPolicy memory,
    SkillVerificationPolicy verification
) {}
