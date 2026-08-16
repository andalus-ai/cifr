package tech.kayys.aljabr.engine.process;

import java.util.Map;

public interface EnvironmentPolicy {

    Map<String, String> build(EnvironmentMode mode, Map<String, String> customVariables);
}
