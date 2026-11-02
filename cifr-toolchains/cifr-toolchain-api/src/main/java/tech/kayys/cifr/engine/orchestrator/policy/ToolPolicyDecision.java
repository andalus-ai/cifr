package tech.kayys.cifr.engine.orchestrator.policy;

public record ToolPolicyDecision(
    boolean allowed,
    boolean requiresApproval,
    String reason,
    ExecutionBudget budget
) {
    public static ToolPolicyDecision allow(ExecutionBudget budget) {
        return new ToolPolicyDecision(true, false, "Allowed by safety policy", budget);
    }

    public static ToolPolicyDecision requireApproval(String reason, ExecutionBudget budget) {
        return new ToolPolicyDecision(true, true, reason, budget);
    }

    public static ToolPolicyDecision deny(String reason) {
        return new ToolPolicyDecision(false, false, reason, null);
    }
}
