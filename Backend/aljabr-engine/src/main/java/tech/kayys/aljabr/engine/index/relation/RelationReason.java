package tech.kayys.aljabr.engine.index.relation;

public enum RelationReason {
    DIRECT_REFERENCE,
    TEST_TARGET,
    IMPORT,
    DEPENDENCY,
    SAME_MODULE,
    SAME_SYMBOL,
    RECENT_CHANGE,
    CONFIGURATION,
    TEXT_SIMILARITY
}
