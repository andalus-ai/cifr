package tech.kayys.cifr.engine.skill;

import java.nio.file.Path;
import java.util.Map;

public record SkillContext(
    String taskId,
    String executionId,
    Path workspaceRoot,
    Map<String, Object> parameters
) {
    public SkillContext {
        parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
    }
}
