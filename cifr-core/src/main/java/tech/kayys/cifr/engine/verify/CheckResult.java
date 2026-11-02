package tech.kayys.cifr.engine.verify;

public record CheckResult(
    String checkName,
    boolean passed,
    String message
) {}
