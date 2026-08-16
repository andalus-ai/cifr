package tech.kayys.aljabr.engine.context;

import java.util.List;

public interface ContextBudgeter {

    ContextSection budgetSection(ContextSection section, int maxTokens);

    List<ContextSection> budgetAll(List<ContextSection> sections, ContextBudget budget);
}
