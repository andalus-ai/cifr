package tech.kayys.cifr.engine.memory;

/**
 * Action decision for forgetting or decommissioning retained knowledge.
 */
public enum ForgetDecision {
    REVOKE,
    ARCHIVE,
    FORGET,
    REJECT
}
