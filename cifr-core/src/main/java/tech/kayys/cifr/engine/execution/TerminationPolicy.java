package tech.kayys.cifr.engine.execution;

/**
 * Policy determining whether an execution has satisfied its completion criteria or should terminate.
 */
public interface TerminationPolicy {
    TerminationDecision evaluate(ExecutionContext context, BudgetStatus budgetStatus);
}
