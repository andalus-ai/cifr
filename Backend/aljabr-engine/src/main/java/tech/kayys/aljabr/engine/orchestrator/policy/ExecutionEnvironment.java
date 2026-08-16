package tech.kayys.aljabr.engine.orchestrator.policy;

import java.util.Map;
import java.util.Set;

public record ExecutionEnvironment(
    Map<String, String> variables,
    Set<String> allowedInheritedVariables,
    NetworkPolicy networkPolicy
) {
    public ExecutionEnvironment {
        variables = variables != null ? Map.copyOf(variables) : Map.of();
        allowedInheritedVariables = allowedInheritedVariables != null ? Set.copyOf(allowedInheritedVariables) : Set.of();
        networkPolicy = networkPolicy != null ? networkPolicy : NetworkPolicy.DENY;
    }

    public static ExecutionEnvironment defaultSandboxed() {
        return new ExecutionEnvironment(Map.of(), Set.of("PATH", "JAVA_HOME", "HOME", "USER"), NetworkPolicy.DENY);
    }
}
