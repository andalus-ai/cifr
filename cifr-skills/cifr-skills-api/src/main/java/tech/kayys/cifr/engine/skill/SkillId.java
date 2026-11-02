package tech.kayys.cifr.engine.skill;

public record SkillId(String value) {
    public SkillId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SkillId cannot be null or empty");
        }
    }

    public static SkillId of(String value) {
        return new SkillId(value);
    }
}
