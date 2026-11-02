package tech.kayys.cifr.engine.verification.graph;

public enum CompletionDecision {
    COMPLETE,
    COMPLETE_WITH_WARNINGS,
    NEED_MORE_VERIFICATION,
    NEED_REPAIR,
    NEED_USER_INPUT,
    BLOCKED
}
