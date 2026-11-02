package tech.kayys.cifr.engine.skill;

public record SkillContextPolicy(
    String strategy,
    int maxEvidence,
    int compressionThresholdTokens
) {
    public static SkillContextPolicy progressive() {
        return new SkillContextPolicy("progressive", 12, 4000);
    }

    public static SkillContextPolicy minimal() {
        return new SkillContextPolicy("minimal", 6, 2000);
    }
}
