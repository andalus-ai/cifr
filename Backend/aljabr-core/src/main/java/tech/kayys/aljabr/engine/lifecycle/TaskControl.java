package tech.kayys.aljabr.engine.lifecycle;

/**
 * Control commands to pause, cancel, or smoothly stop a task.
 */
public enum TaskControl {
    RUN,
    PAUSE,
    CANCEL,
    STOP_AFTER_CURRENT_ACTION
}
