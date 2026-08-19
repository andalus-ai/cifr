package tech.kayys.aljabr.engine.verify;

public record CheckResult(
    String checkName,
    boolean passed,
    String message
) {}
