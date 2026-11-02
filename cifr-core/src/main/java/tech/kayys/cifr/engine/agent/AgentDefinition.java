package tech.kayys.cifr.engine.agent;

import java.util.List;

/**
 * Declarative configuration and behavioral policy defining an Agent without embedding runtime logic.
 */
public record AgentDefinition(
    AgentId id,
    String name,
    String description,
    AgentInstructions instructions,
    List<String> skillIds,
    AgentPreferences preferences
) {
    public AgentDefinition {
        skillIds = skillIds != null ? List.copyOf(skillIds) : List.of();
    }

    public static AgentDefinition of(AgentId id, String name, String description, AgentInstructions instructions, List<String> skillIds) {
        return new AgentDefinition(id, name, description, instructions, skillIds, AgentPreferences.local());
    }
}
