package tech.kayys.aljabr.engine.skill;

import java.util.List;
import java.util.Map;

public record SkillResult(
    boolean isSuccess,
    String summary,
    Map<String, Object> outputData,
    List<String> evidenceIds
) {
    public SkillResult {
        outputData = outputData != null ? Map.copyOf(outputData) : Map.of();
        evidenceIds = evidenceIds != null ? List.copyOf(evidenceIds) : List.of();
    }

    public static SkillResult success(String summary) {
        return new SkillResult(true, summary, Map.of(), List.of());
    }

    public static SkillResult success(String summary, Map<String, Object> outputData) {
        return new SkillResult(true, summary, outputData, List.of());
    }

    public static SkillResult success(String summary, Map<String, Object> outputData, List<String> evidenceIds) {
        return new SkillResult(true, summary, outputData, evidenceIds);
    }

    public static SkillResult failure(String summary) {
        return new SkillResult(false, summary, Map.of(), List.of());
    }

    public static SkillResult failure(String summary, Map<String, Object> outputData) {
        return new SkillResult(false, summary, outputData, List.of());
    }
}
