package tech.kayys.aljabr.engine.skill.routing;

import tech.kayys.aljabr.engine.skill.SkillContext;
import tech.kayys.aljabr.engine.skill.SkillDescriptor;

public interface ReasoningGate {

    ReasoningDecision evaluate(String skillId, SkillContext context, SkillDescriptor descriptor);
}
