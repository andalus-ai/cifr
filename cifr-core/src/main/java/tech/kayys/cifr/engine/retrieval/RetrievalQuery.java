package tech.kayys.cifr.engine.retrieval;

import java.util.Optional;
import java.util.Set;

/**
 * Objective-driven query executed by the RetrievalEngine.
 */
public record RetrievalQuery(
    String objective,
    Optional<String> currentStepId,
    Set<String> entities,
    Set<String> topics,
    RetrievalScope scope,
    RetrievalBudget budget
) {
    public RetrievalQuery {
        entities = entities != null ? Set.copyOf(entities) : Set.of();
        topics = topics != null ? Set.copyOf(topics) : Set.of();
    }

    public static RetrievalQuery of(String objective, Set<String> entities) {
        return new RetrievalQuery(objective, Optional.empty(), entities, Set.of(), RetrievalScope.ALL, RetrievalBudget.defaults());
    }
}
