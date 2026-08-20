package tech.kayys.aljabr.orchestration.strategy;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ReActExecutionStrategy extends AbstractAgentExecutionStrategy {

    @Override
    public String id() {
        return "react";
    }

    @Override
    public String getSystemPromptFragment() {
        return "You must operate in a strict Reason-Act-Observe loop. Always output your internal 'thought' explaining what you want to do, then emit a TOOL_CALL. After receiving an OBSERVATION from the environment, evaluate if you have reached the goal. If so, emit FINAL_ANSWER.";
    }
}
