package tech.kayys.aljabr.engine.runtime.recovery;

/**
 * Recovery states of an agent runtime during post-crash initialization.
 */
public enum RecoveryState {
    IDLE,
    RECOVERY_REQUIRED,
    RECONCILING,
    RECOVERED,
    RECOVERY_FAILED
}
