package tech.kayys.aljabr.engine.agent;

import java.util.UUID;

/**
 * Unique identifier for an agent definition.
 */
public record AgentId(String value) {
    public static AgentId of(String value) {
        return new AgentId(value);
    }

    public static AgentId generate() {
        return new AgentId("agent-" + UUID.randomUUID());
    }
}
