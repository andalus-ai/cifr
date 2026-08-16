package tech.kayys.aljabr.engine.process;

public enum ProcessState {
    CREATED,
    STARTING,
    RUNNING,
    EXITED,
    TIMED_OUT,
    TERMINATING,
    TERMINATED,
    FAILED_TO_START
}
