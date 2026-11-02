package tech.kayys.cifr.engine.task.step;

public enum StepStatus {
    PENDING,
    READY,
    RUNNING,
    WAITING,
    BLOCKED,
    COMPLETED,
    FAILED,
    CANCELLED,
    SKIPPED
}
