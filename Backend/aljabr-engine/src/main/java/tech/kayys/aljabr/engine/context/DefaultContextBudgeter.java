package tech.kayys.aljabr.engine.context;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class DefaultContextBudgeter implements ContextBudgeter {

    @Override
    public ContextSection budgetSection(ContextSection section, int maxTokens) {
        if (section == null) return null;
        if (section.estimatedTokens() <= maxTokens) {
            return section;
        }

        int maxChars = Math.max(100, maxTokens * 4);
        String content = section.content();
        if (content.length() <= maxChars) {
            return section;
        }

        String truncated = content.substring(0, maxChars) + "\n... [truncated to fit token budget]";
        return ContextSection.of(section.header(), truncated, section.priority());
    }

    @Override
    public List<ContextSection> budgetAll(List<ContextSection> sections, ContextBudget budget) {
        if (sections == null || sections.isEmpty()) return List.of();
        ContextBudget b = budget != null ? budget : ContextBudget.defaultBudget();

        // Sort by priority descending (higher number = higher priority)
        List<ContextSection> sorted = new ArrayList<>(sections);
        sorted.sort(Comparator.comparingInt(ContextSection::priority).reversed());

        List<ContextSection> budgeted = new ArrayList<>();
        int remainingTokens = b.totalBudgetTokens();

        for (ContextSection sec : sorted) {
            if (remainingTokens <= 50) {
                // Out of budget, skip lower priority sections
                break;
            }

            int sectionLimit = Math.min(sec.estimatedTokens(), remainingTokens);
            ContextSection fitted = budgetSection(sec, sectionLimit);
            budgeted.add(fitted);
            remainingTokens -= fitted.estimatedTokens();
        }

        return budgeted;
    }
}
