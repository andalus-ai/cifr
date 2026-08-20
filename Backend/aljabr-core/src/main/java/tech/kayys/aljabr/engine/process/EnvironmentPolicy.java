package tech.kayys.aljabr.engine.process;

import java.util.Map;

public record EnvironmentPolicy(
    EnvironmentMode mode,
    Map<String, String> environmentVariables,
    boolean inheritHostEnv
) {}
