package tech.kayys.aljabr.engine.memory;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Default implementation of {@link MemoryRanker} prioritizing higher scores and recency.
 */
@ApplicationScoped
public class DefaultMemoryRanker implements MemoryRanker {

    @Override
    public List<MemoryMatch> rank(List<MemoryMatch> matches) {
        if (matches == null || matches.isEmpty()) {
            return List.of();
        }

        List<MemoryMatch> sorted = new ArrayList<>(matches);
        sorted.sort(Comparator.comparingDouble(MemoryMatch::score).reversed());
        return sorted;
    }
}
