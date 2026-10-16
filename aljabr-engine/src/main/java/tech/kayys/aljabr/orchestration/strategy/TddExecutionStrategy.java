package tech.kayys.aljabr.orchestration.strategy;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TddExecutionStrategy extends AbstractAgentExecutionStrategy {

    @Override
    public String id() {
        return "tdd";
    }

    @Override
    public String getSystemPromptFragment() {
        return "You must operate in a strict Test-Driven Development (TDD) loop. 1) Write a failing test. 2) Run the test. 3) Write code to pass the test. 4) Run the test again to verify.";
    }
}
