package tech.kayys.aljabr.engine.skill;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class DefaultSkillRegistry implements SkillRegistry {

    private final Map<String, CodingSkill> skills = new ConcurrentHashMap<>();

    @Inject
    Instance<CodingSkill> discoveredSkills;

    @PostConstruct
    void init() {
        if (discoveredSkills != null) {
            for (CodingSkill skill : discoveredSkills) {
                register(skill);
            }
        }
    }

    @Override
    public Optional<CodingSkill> find(String skillName) {
        if (skillName == null) return Optional.empty();
        return Optional.ofNullable(skills.get(skillName.toLowerCase()));
    }

    public Optional<CodingSkill> find(SkillId id) {
        if (id == null) return Optional.empty();
        return find(id.value());
    }

    @Override
    public List<SkillDescriptor> listDescriptors() {
        return skills.values().stream()
            .map(CodingSkill::descriptor)
            .toList();
    }

    @Override
    public void register(CodingSkill skill) {
        if (skill == null) return;
        String idStr = skill.id() != null ? skill.id().value().toLowerCase() : skill.descriptor().name().toLowerCase();
        skills.put(idStr, skill);
    }
}
