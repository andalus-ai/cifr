package tech.kayys.cifr.engine.skill;

public record SkillMemoryPolicy(boolean persistHypotheses, boolean persistDecisions, boolean persistEvidence) {
    public static SkillMemoryPolicy full() {
        return new SkillMemoryPolicy(true, true, true);
    }

    public static SkillMemoryPolicy minimal() {
        return new SkillMemoryPolicy(false, true, false);
    }
}
