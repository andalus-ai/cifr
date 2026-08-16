package tech.kayys.aljabr.engine.process;

public record ProcessId(
    String executionId,
    long osPid
) {}
