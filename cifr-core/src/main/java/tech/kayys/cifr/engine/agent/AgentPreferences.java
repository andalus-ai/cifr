package tech.kayys.cifr.engine.agent;

/**
 * Model and runtime preferences configuring agent execution.
 */
public record AgentPreferences(
    String modelTier,
    String responseStyle
) {
    public static AgentPreferences local() {
        return new AgentPreferences("local", "concise");
    }

    public static AgentPreferences cloud() {
        return new AgentPreferences("cloud", "detailed");
    }
}
