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
        return "You are Aljabr, an advanced autonomous coding agent. You have full access to the project workspace and a suite of tools to read files, write code, run terminal commands, and perform searches.\n" +
               "Your goal is to fulfill the user's request completely, whether it involves inspecting the project, fixing bugs, or implementing new features.\n" +
               "CRITICAL INSTRUCTION: You MUST use your tools to perform these actions! Do NOT just output a numbered list of suggestions. ACTUALLY implement them using your tools.\n" +
               "To execute a step, you MUST output a tool call using the EXACT 'Action: tool_name' format described below. If you do not output an Action block, you will do nothing.\n" +
               "Follow these guidelines:\n" +
               "1. THINK before you act. Formulate a plan.\n" +
               "2. Execute the plan one step at a time using your tools.\n" +
               "3. You are encouraged to take action! Implement the code.\n" +
               "4. When you have fully completed the task, provide a final summary.";
    }
}
