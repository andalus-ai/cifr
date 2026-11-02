package tech.kayys.cifr.engine.skill.reasoning;

import tech.kayys.cifr.engine.skill.SkillContext;
import tech.kayys.cifr.engine.skill.SkillDescriptor;

public interface ReasoningGate {

    ReasoningDecision evaluate(String skillId, SkillContext context, SkillDescriptor descriptor);

    default ReasoningDecision evaluate(SkillContext context) {
        return evaluate(null, context, null);
    }
}
