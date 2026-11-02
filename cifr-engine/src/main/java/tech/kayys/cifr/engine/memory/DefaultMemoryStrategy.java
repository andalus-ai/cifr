package tech.kayys.cifr.engine.memory;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class DefaultMemoryStrategy implements MemoryStrategy {

    @Override
    public Optional<MemoryPolicy> evaluate(MemoryCandidate candidate) {
        if (candidate.content() == null || candidate.content().trim().isEmpty()) {
            return Optional.empty();
        }

        // Extremely simple heuristic for MVP:
        // If the candidate mentions "prefer", it is a USER preference.
        // If the candidate mentions "project" or "always", it's a PROJECT rule.
        // Otherwise, we default to SESSION.

        String text = candidate.content().toLowerCase();
        
        MemoryScope scope = MemoryScope.SESSION;
        double importance = 0.5;
        boolean permanent = false;
        boolean sensitive = false;

        if (candidate.explicitlyStatedScope().isPresent()) {
            try {
                scope = MemoryScope.valueOf(candidate.explicitlyStatedScope().get().toUpperCase());
                if (scope == MemoryScope.USER || scope == MemoryScope.PROJECT) {
                    permanent = true;
                    importance = 0.8;
                }
            } catch (IllegalArgumentException e) {
                // fall back to heuristics
            }
        } else {
            if (text.contains("prefer") || text.contains("i like")) {
                scope = MemoryScope.USER;
                permanent = true;
                importance = 0.9;
            } else if (text.contains("project") || text.contains("must always") || text.contains("convention")) {
                scope = MemoryScope.PROJECT;
                permanent = true;
                importance = 0.8;
            } else if (text.contains("secret") || text.contains("password") || text.contains("token")) {
                sensitive = true;
                // We might even reject storing sensitive data by returning Optional.empty()
                // But for now we just flag it.
            }
        }

        return Optional.of(new MemoryPolicy(scope, importance, permanent, sensitive));
    }
}
