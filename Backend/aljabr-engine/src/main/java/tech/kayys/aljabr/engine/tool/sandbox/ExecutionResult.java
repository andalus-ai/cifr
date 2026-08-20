package tech.kayys.aljabr.engine.tool.sandbox;
public record ExecutionResult(int exitCode, String stdout, String stderr, boolean timedOut) {
    public boolean isSuccess() { return exitCode == 0 && !timedOut; }
}
