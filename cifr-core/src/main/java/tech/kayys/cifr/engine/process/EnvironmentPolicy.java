package tech.kayys.cifr.engine.process;

import java.util.Map;

public record EnvironmentPolicy(
    EnvironmentMode mode,
    Map<String, String> environmentVariables,
    boolean inheritHostEnv
) {}
