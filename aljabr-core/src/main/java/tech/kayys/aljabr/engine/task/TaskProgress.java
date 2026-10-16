package tech.kayys.aljabr.engine.task;

import java.util.List;

public record TaskProgress(
    List<TaskPhase> completedPhases,
    TaskPhase currentPhase,
    int iterationCount
) {
    public TaskProgress {
        completedPhases = completedPhases != null ? List.copyOf(completedPhases) : List.of();
    }

    public static TaskProgress initial() {
        return new TaskProgress(List.of(), TaskPhase.CREATED, 0);
    }
}
