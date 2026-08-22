package tech.kayys.aljabr.engine.context;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Default implementation of {@link ContextBudgeter}.
 *
 * <p>Truncates section content by character count (4 chars ≈ 1 token) when its estimated
 * token count would exceed {@code tokenBudget}.  A sentinel suffix is appended so downstream
 * consumers know truncation occurred.
 */
@ApplicationScoped
public class DefaultContextBudgeter implements ContextBudgeter {

    private static final String TRUNCATION_MARKER = "\n[truncated to fit token budget]";
    private static final int CHARS_PER_TOKEN = 4;

    @Override
    public ContextSection budgetSection(ContextSection section, int tokenBudget) {
        if (section == null) {
            return null;
        }

        int currentTokens = section.estimatedTokens();
        if (currentTokens <= tokenBudget) {
            return section; // already fits
        }

        // Max chars we can keep
        int maxChars = Math.max(0, (tokenBudget * CHARS_PER_TOKEN) - TRUNCATION_MARKER.length());
        String content = section.content();
        String truncated = (content != null && content.length() > maxChars)
            ? content.substring(0, maxChars) + TRUNCATION_MARKER
            : (content != null ? content : "") + TRUNCATION_MARKER;

        int newTokens = Math.max(1, truncated.length() / CHARS_PER_TOKEN);
        return new ContextSection(section.header(), truncated, newTokens, section.priority());
    }
}
