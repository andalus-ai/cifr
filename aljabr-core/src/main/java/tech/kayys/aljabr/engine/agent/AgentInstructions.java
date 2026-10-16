package tech.kayys.aljabr.engine.agent;

import java.util.List;

/**
 * Core behavioral identity and operational principles for an Agent.
 */
public record AgentInstructions(
    String role,
    List<String> principles
) {
    public AgentInstructions {
        principles = principles != null ? List.copyOf(principles) : List.of();
    }

    public static AgentInstructions of(String role, List<String> principles) {
        return new AgentInstructions(role, principles);
    }
}
