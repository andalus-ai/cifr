package tech.kayys.aljabr.engine.skill;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.annotation.PostConstruct;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class DefaultSkillRegistry implements SkillRegistry {

    private final Map<String, CodingSkill> skills = new ConcurrentHashMap<>();

    @Inject
    Instance<CodingSkill> discoveredSkills;

    public DefaultSkillRegistry() {
    }

    public DefaultSkillRegistry(Instance<CodingSkill> discoveredSkills) {
        this.discoveredSkills = discoveredSkills;
        init();
    }

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
        if (skillName == null) {
            return Optional.empty();
        }
        CodingSkill skill = skills.get(skillName);
        if (skill == null) {
            // Also try matching by descriptor name or id case-insensitively
            return skills.values().stream()
                .filter(s -> s.descriptor() != null &&
                    (skillName.equalsIgnoreCase(s.descriptor().id()) ||
                     skillName.equalsIgnoreCase(s.descriptor().name())))
                .findFirst();
        }
        return Optional.of(skill);
    }

    @Override
    public List<SkillDescriptor> listDescriptors() {
        return skills.values().stream()
            .map(CodingSkill::descriptor)
            .filter(d -> d != null)
            .collect(Collectors.toList());
    }

    @Override
    public void register(CodingSkill skill) {
        if (skill != null && skill.descriptor() != null) {
            skills.put(skill.descriptor().id(), skill);
        }
    }
}
