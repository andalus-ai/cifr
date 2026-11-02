package tech.kayys.cifr.engine.skill.runtime;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.skill.SkillDefinition;
import tech.kayys.cifr.engine.skill.SkillProjection;
import tech.kayys.cifr.engine.skill.SkillSession;

import java.util.List;

@ApplicationScoped
public class SkillProjectionEngine {

    public SkillProjection project(SkillDefinition skill, SkillSession session) {
        if (skill == null) {
            return new SkillProjection("", List.of(), session != null ? session.phase() : "CREATED", List.of(), "structured_output");
        }

        String objective = skill.objective() != null ? skill.objective().description() : "";
        List<String> rules = skill.objective() != null ? skill.objective().principles() : List.of();
        List<String> caps = skill.capabilities() != null
            ? skill.capabilities().required().stream().toList()
            : List.of();
        String currentPhase = session != null ? session.phase() : "CREATED";

        return new SkillProjection(
            objective,
            rules,
            currentPhase,
            caps,
            "structured_output"
        );
    }
}
