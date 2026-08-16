package tech.kayys.aljabr.engine.skill;

import io.smallrye.mutiny.Uni;

public interface CodingSkill {

    SkillContract contract();

    default SkillDescriptor descriptor() {
        return contract() != null ? contract().descriptor() : null;
    }

    Uni<SkillResult> execute(SkillContext context);
}
