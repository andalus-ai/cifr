package tech.kayys.cifr.engine.skill;

public record SkillVersion(int major, int minor) {
    public static SkillVersion v1() {
        return new SkillVersion(1, 0);
    }

    @Override
    public String toString() {
        return major + "." + minor;
    }
}
