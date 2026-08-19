package tech.kayys.aljabr.engine.skill;

import java.util.List;
import java.util.Optional;

public interface SkillRegistry {

    Optional<CodingSkill> find(String skillName);

    List<SkillDescriptor> listDescriptors();

    void register(CodingSkill skill);
}
