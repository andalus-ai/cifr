package tech.kayys.aljabr.engine.exec;

public record ExecutionBudget(
    int maxConcurrentProcesses,
    int maxConcurrentBuilds,
    int maxConcurrentTests
) {
    public static ExecutionBudget localDefault() {
        return new ExecutionBudget(4, 1, 2);
    }
}
