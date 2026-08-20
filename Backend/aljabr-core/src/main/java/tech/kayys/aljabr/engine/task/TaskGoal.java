package tech.kayys.aljabr.engine.task;

import java.util.List;

public record TaskGoal(
    String description,
    List<String> requirements,
    List<String> constraints
) {
    public TaskGoal {
        requirements = requirements != null ? List.copyOf(requirements) : List.of();
        constraints = constraints != null ? List.copyOf(constraints) : List.of();
    }

    public static TaskGoal of(String description) {
        return new TaskGoal(description, List.of(), List.of());
    }
}
