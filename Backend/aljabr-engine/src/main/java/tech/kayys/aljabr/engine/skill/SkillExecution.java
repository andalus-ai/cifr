package tech.kayys.aljabr.engine.skill;

import java.time.Instant;

public record SkillExecution(
    String skillName,
    SkillContext context,
    SkillResult result,
    Instant executedAt
) {}
