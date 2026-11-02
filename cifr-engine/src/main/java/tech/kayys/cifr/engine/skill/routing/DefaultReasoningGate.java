package tech.kayys.cifr.engine.skill.routing;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.skill.ReasoningMode;
import tech.kayys.cifr.engine.skill.SkillContext;
import tech.kayys.cifr.engine.skill.SkillDescriptor;
import tech.kayys.cifr.engine.skill.reasoning.ReasoningDecision;
import tech.kayys.cifr.engine.skill.reasoning.ReasoningGate;

@ApplicationScoped
public class DefaultReasoningGate implements ReasoningGate {

    @Override
    public ReasoningDecision evaluate(String skillId, SkillContext context, SkillDescriptor descriptor) {
        if (descriptor == null) {
            return ReasoningDecision.SKIP_MODEL;
        }

        ReasoningMode mode = descriptor.reasoningMode();
        if (mode == null || mode == ReasoningMode.NONE) {
            return ReasoningDecision.SKIP_MODEL;
        }

        if (mode == ReasoningMode.REQUIRED) {
            return ReasoningDecision.USE_MODEL;
        }

        if (mode == ReasoningMode.OPTIONAL) {
            // If explicit parameters (like 'symbol' or 'file') exist without ambiguity, skip model
            if (context != null && context.parameters() != null) {
                boolean hasTarget = context.parameters().containsKey("symbol") || context.parameters().containsKey("file");
                boolean isAmbiguous = (Boolean) context.parameters().getOrDefault("ambiguous", false);
                if (hasTarget && !isAmbiguous) {
                    return ReasoningDecision.SKIP_MODEL;
                }
            }
            return ReasoningDecision.USE_MODEL;
        }

        if (mode == ReasoningMode.ESCALATABLE) {
            if (context != null && context.parameters() != null) {
                String errorLog = (String) context.parameters().get("errorLog");
                // If error log indicates multi-layer architectural failure or ambiguous cause, escalate
                if (errorLog != null && (errorLog.contains("Caused by:") || errorLog.lines().count() > 15)) {
                    return ReasoningDecision.ESCALATE_MODEL;
                }
            }
            return ReasoningDecision.SKIP_MODEL;
        }

        return ReasoningDecision.SKIP_MODEL;
    }
}
