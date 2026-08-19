package tech.kayys.aljabr.engine.skill.reasoning;

import tech.kayys.aljabr.engine.skill.SkillContext;
import tech.kayys.aljabr.engine.skill.SkillDescriptor;

public interface ReasoningGate {

    ReasoningDecision evaluate(String skillId, SkillContext context, SkillDescriptor descriptor);

    default ReasoningDecision evaluate(SkillContext context) {
        return evaluate(null, context, null);
    }
}
