package tech.kayys.cifr.engine.exec.intelligence;

import java.time.Duration;

/** Immutable outcome of one process execution. */
public record ProcessResult(
    int exitCode,
    Duration duration,
    ProcessStatus status,
    String stdout,
    String stderr,
    String processId
) {
    /** Source-compatible constructor for existing callers. */
    public ProcessResult(int exitCode, Duration duration, ProcessStatus status, String stdout, String stderr) {
        this(exitCode, duration, status, stdout, stderr, null);
    }

    public boolean isSuccess() {
        return status == ProcessStatus.SUCCESS && exitCode == 0;
    }
}
