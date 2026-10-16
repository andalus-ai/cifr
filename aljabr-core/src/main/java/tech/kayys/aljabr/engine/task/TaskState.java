package tech.kayys.aljabr.engine.task;

public record TaskState(
    TaskId taskId,
    TaskStatus status,
    TaskPhase phase,
    long revision,
    TaskGoal goal,
    TaskProgress progress
) {
    public TaskState withPhase(TaskPhase newPhase) {
        return new TaskState(
            taskId,
            status,
            newPhase,
            revision + 1,
            goal,
            new TaskProgress(progress != null ? progress.completedPhases() : java.util.List.of(), newPhase, progress != null ? progress.iterationCount() : 0)
        );
    }

    public TaskState withStatus(TaskStatus newStatus) {
        return new TaskState(taskId, newStatus, phase, revision + 1, goal, progress);
    }
}
