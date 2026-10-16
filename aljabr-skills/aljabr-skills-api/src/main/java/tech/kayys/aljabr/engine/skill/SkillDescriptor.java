package tech.kayys.aljabr.engine.skill;

import java.util.Map;
import java.util.Set;

public record SkillDescriptor(
    String id,
    String name,
    String description,
    SkillCategory category,
    ReasoningMode reasoningMode,
    boolean mutatesWorkspace,
    Set<String> requiredCapabilities,
    Map<String, Object> inputSchema,
    Map<String, Object> outputSchema
) {
    public SkillDescriptor {
        category = category != null ? category : SkillCategory.DISCOVERY;
        reasoningMode = reasoningMode != null ? reasoningMode : ReasoningMode.NONE;
        requiredCapabilities = requiredCapabilities != null ? Set.copyOf(requiredCapabilities) : Set.of();
        inputSchema = inputSchema != null ? Map.copyOf(inputSchema) : Map.of();
        outputSchema = outputSchema != null ? Map.copyOf(outputSchema) : Map.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private String description;
        private SkillCategory category = SkillCategory.DISCOVERY;
        private ReasoningMode reasoningMode = ReasoningMode.NONE;
        private boolean mutatesWorkspace = false;
        private Set<String> requiredCapabilities = Set.of();
        private Map<String, Object> inputSchema = Map.of();
        private Map<String, Object> outputSchema = Map.of();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder category(SkillCategory category) {
            this.category = category;
            return this;
        }

        public Builder reasoningMode(ReasoningMode reasoningMode) {
            this.reasoningMode = reasoningMode;
            return this;
        }

        public Builder mutatesWorkspace(boolean mutatesWorkspace) {
            this.mutatesWorkspace = mutatesWorkspace;
            return this;
        }

        public Builder requiredCapabilities(Set<String> requiredCapabilities) {
            this.requiredCapabilities = requiredCapabilities;
            return this;
        }

        public Builder inputSchema(Map<String, Object> inputSchema) {
            this.inputSchema = inputSchema;
            return this;
        }

        public Builder outputSchema(Map<String, Object> outputSchema) {
            this.outputSchema = outputSchema;
            return this;
        }

        public SkillDescriptor build() {
            return new SkillDescriptor(
                id != null ? id : (name != null ? name.toLowerCase().replace(' ', '.') : "unknown"),
                name != null ? name : "Unnamed Skill",
                description != null ? description : "",
                category,
                reasoningMode,
                mutatesWorkspace,
                requiredCapabilities,
                inputSchema,
                outputSchema
            );
        }
    }
}
