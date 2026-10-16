package tech.kayys.aljabr.orchestration.strategy;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PlanAndSolveExecutionStrategy extends AbstractAgentExecutionStrategy {

    @Override
    public String id() {
        return "plan_and_solve";
    }

    @Override
    public String getSystemPromptFragment() {
        return "You must first output a detailed step-by-step plan (checklist) to solve the problem. Then, execute the plan one step at a time, emitting a TOOL_CALL for each step. After receiving an OBSERVATION, mark the step complete and proceed to the next until finished.";
    }
}
