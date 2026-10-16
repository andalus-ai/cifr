package tech.kayys.aljabr.engine.skill;

public record SkillVerificationPolicy(boolean required, boolean failFast, double minConfidence) {
    public static SkillVerificationPolicy strict() {
        return new SkillVerificationPolicy(true, true, 0.8);
    }

    public static SkillVerificationPolicy lenient() {
        return new SkillVerificationPolicy(true, false, 0.5);
    }

    public static SkillVerificationPolicy none() {
        return new SkillVerificationPolicy(false, false, 0.0);
    }
}
