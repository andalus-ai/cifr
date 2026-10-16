package tech.kayys.aljabr.engine.debug;

public enum DebugState {
    FAILURE_DETECTED,
    TRIAGING,
    HYPOTHESIS_GENERATION,
    EXPERIMENT_SELECTION,
    EXPERIMENT_RUNNING,
    ANALYZING,
    REPAIRING,
    VERIFYING,
    RESOLVED,
    BLOCKED
}
