package tech.kayys.aljabr.engine.exec;

public record ExecutionCapabilities(
    boolean processIsolation,
    boolean containerSupport,
    boolean networkIsolation,
    boolean resourceLimits,
    boolean filesystemIsolation
) {
    public static ExecutionCapabilities localDefault() {
        return new ExecutionCapabilities(true, false, false, false, true);
    }
}
