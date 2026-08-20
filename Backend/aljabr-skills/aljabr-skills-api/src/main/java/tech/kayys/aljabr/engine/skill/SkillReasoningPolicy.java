package tech.kayys.aljabr.engine.skill;

public record SkillReasoningPolicy(String strategy, boolean escalateOnUncertainty) {
    public static SkillReasoningPolicy evidenceFirst() {
        return new SkillReasoningPolicy("evidence_first", true);
    }

    public static SkillReasoningPolicy direct() {
        return new SkillReasoningPolicy("direct", false);
    }
}
