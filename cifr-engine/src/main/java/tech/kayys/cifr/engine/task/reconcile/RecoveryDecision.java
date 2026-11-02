package tech.kayys.cifr.engine.task.reconcile;

public enum RecoveryDecision {
    CONTINUE,
    MARK_COMPLETED,
    ROLLBACK,
    REPAIR,
    BLOCK
}
