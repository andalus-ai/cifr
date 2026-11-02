package tech.kayys.cifr.engine.index;

import io.smallrye.mutiny.Uni;
import java.util.List;

/**
 * Resolves compact, ranked repository context fragments for model ingestion,
 * respecting token budgets without dumb truncation.
 */
public interface RepositoryContextResolver {

    Uni<List<String>> resolve(RepositoryContextRequest request);
}
