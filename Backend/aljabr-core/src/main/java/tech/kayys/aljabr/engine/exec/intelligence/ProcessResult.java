package tech.kayys.aljabr.engine.exec.intelligence;

import java.time.Duration;

public record ProcessResult(
    int exitCode,
    Duration duration,
    ProcessStatus status,
    String stdout,
    String stderr
) {
    public boolean isSuccess() {
        return status == ProcessStatus.SUCCESS && exitCode == 0;
    }
}
