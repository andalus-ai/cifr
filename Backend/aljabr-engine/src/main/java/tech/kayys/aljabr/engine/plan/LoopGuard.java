package tech.kayys.aljabr.engine.plan;

public class LoopGuard {

    private final LoopBudget budget;
    private int iterations;
    private int modelCalls;
    private int toolCalls;
    private int replans;

    public LoopGuard(LoopBudget budget) {
        this.budget = budget != null ? budget : LoopBudget.localDefault();
    }

    public boolean canIterate() {
        return iterations < budget.maxIterations() &&
               modelCalls < budget.maxModelCalls() &&
               toolCalls < budget.maxToolCalls() &&
               replans < budget.maxReplans();
    }

    public void recordIteration() {
        this.iterations++;
    }

    public void recordModelCall() {
        this.modelCalls++;
    }

    public void recordToolCall() {
        this.toolCalls++;
    }

    public void recordReplan() {
        this.replans++;
    }

    public LoopBudget getBudget() {
        return budget;
    }

    public int getIterations() {
        return iterations;
    }
}
