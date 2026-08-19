package tech.kayys.aljabr.engine.skill.router;

import io.smallrye.mutiny.Uni;
import tech.kayys.aljabr.engine.skill.SkillContext;
import tech.kayys.aljabr.engine.skill.SkillId;

public interface SkillRouter {

    Uni<SkillId> select(SkillContext context);
}
