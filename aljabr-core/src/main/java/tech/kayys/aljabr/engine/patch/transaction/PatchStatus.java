package tech.kayys.aljabr.engine.patch.transaction;

public enum PatchStatus {
    APPLIED,
    REJECTED,
    PRECONDITION_FAILED,
    CONFLICT,
    VALIDATION_FAILED,
    BUDGET_EXCEEDED
}
