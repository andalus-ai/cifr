package tech.kayys.aljabr.engine.planning;

/**
 * Conditions required for a dependency between task steps to be satisfied.
 */
public enum DependencyCondition {
    SUCCESS,
    COMPLETION,
    EVIDENCE_AVAILABLE,
    DECISION_TRUE,
    RESOURCE_AVAILABLE
}
