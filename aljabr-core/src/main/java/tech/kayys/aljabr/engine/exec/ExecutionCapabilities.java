package tech.kayys.aljabr.engine.exec;

import java.util.Set;

public record ExecutionCapabilities(
    boolean networkAccess,
    boolean filesystemWrite,
    boolean processFork,
    Set<String> allowedCommands
) {
    public ExecutionCapabilities {
        allowedCommands = allowedCommands != null ? Set.copyOf(allowedCommands) : Set.of();
    }

    public static ExecutionCapabilities localDefault() {
        return new ExecutionCapabilities(true, true, true, Set.of());
    }
}
