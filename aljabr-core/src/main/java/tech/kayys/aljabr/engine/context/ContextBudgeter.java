package tech.kayys.aljabr.engine.context;

/**
 * Applies token budget constraints to individual context sections.
 */
public interface ContextBudgeter {

    /**
     * Truncates the section content so its token estimate fits within {@code tokenBudget}.
     */
    ContextSection budgetSection(ContextSection section, int tokenBudget);
}
