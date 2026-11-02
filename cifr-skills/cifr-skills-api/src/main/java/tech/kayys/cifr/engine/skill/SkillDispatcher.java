package tech.kayys.cifr.engine.skill;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;

@ApplicationScoped
public class SkillDispatcher {

    @Inject
    SkillRegistry registry;

    public Uni<SkillExecution> dispatch(SkillId skillId, SkillContext context) {
        CodingSkill skill = registry.find(skillId.value())
            .orElseThrow(() -> new IllegalArgumentException("Unknown skill: " + skillId.value()));

        return skill.execute(context)
            .map(result -> new SkillExecution(
                skillId.value(),
                context,
                result,
                Instant.now()
            ));
    }
}
