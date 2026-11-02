package tech.kayys.cifr.engine.agent;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.cifr.engine.context.ContextPacket;
import tech.kayys.cifr.engine.context.ContextSection;

import java.util.List;
import java.util.Map;

/**
 * Default implementation of {@link PromptCompiler} assembling structured model requests.
 */
@ApplicationScoped
public class DefaultPromptCompiler implements PromptCompiler {

    @Override
    public ModelRequest compile(AgentDefinition agent, List<String> activeSkillInstructions, ContextPacket context) {
        StringBuilder sys = new StringBuilder();
        if (agent != null && agent.instructions() != null) {
            sys.append("Role: ").append(agent.instructions().role()).append("\n");
            sys.append("Principles:\n");
            for (String p : agent.instructions().principles()) {
                sys.append("- ").append(p).append("\n");
            }
        }

        if (activeSkillInstructions != null && !activeSkillInstructions.isEmpty()) {
            sys.append("\nActive Skills:\n");
            for (String s : activeSkillInstructions) {
                sys.append("### ").append(s).append("\n");
            }
        }

        StringBuilder user = new StringBuilder();
        if (context != null && context.sections() != null) {
            for (ContextSection section : context.sections()) {
                user.append("## ").append(section.header()).append(":\n")
                    .append(section.content()).append("\n\n");
            }
        }

        return new ModelRequest(sys.toString().trim(), user.toString().trim(), Map.of());
    }
}
