package tech.kayys.cifr.engine.agent;

import tech.kayys.cifr.engine.context.ContextPacket;

import java.util.List;

/**
 * Compiles an AgentDefinition, active skill instruction blocks, and ContextPacket into a structured ModelRequest.
 */
public interface PromptCompiler {

    ModelRequest compile(
        AgentDefinition agent,
        List<String> activeSkillInstructions,
        ContextPacket context
    );
}
