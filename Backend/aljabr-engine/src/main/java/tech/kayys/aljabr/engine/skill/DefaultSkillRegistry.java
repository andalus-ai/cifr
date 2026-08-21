package tech.kayys.aljabr.engine.skill;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry for coding skills.
 */
@ApplicationScoped
public class DefaultSkillRegistry implements SkillRegistry {

    private final Map<String, CodingSkill> skills = new ConcurrentHashMap<>();

    @Override
    public Optional<CodingSkill> find(String skillName) {
        if (skillName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(skills.get(skillName));
    }

    @Override
    public List<SkillDescriptor> listDescriptors() {
        List<SkillDescriptor> list = new ArrayList<>();
        for (CodingSkill skill : skills.values()) {
            list.add(skill.descriptor());
        }
        return list;
    }

    @Override
    public void register(CodingSkill skill) {
        if (skill != null && skill.descriptor() != null) {
            skills.put(skill.descriptor().id(), skill);
        }
    }
}
