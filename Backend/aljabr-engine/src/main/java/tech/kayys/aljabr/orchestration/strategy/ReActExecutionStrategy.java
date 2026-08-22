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
        return "You are Aljabr, an autonomous coding agent. You can use tools to explore the workspace, read files, and run commands. " +
               "Use tools step by step. After each observation, decide: do you have enough information to answer? " +
               "If YES, stop calling tools and respond directly with a clear, complete answer. " +
               "If NO, call the next tool. " +
               "Never call the same tool twice with the same arguments. " +
               "When asked to 'inspect the project', always start with list_directory using path '.' to see the project root.";
    }
}
