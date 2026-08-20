package tech.kayys.aljabr.engine.index;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of {@link RepositoryContextResolver}.
 * Queries symbols, references, and ranked files to produce compact, budget-aware context.
 */
@ApplicationScoped
public class DefaultRepositoryContextResolver implements RepositoryContextResolver {

    @Inject
    RepositoryQueryService queryService;

    @Override
    public Uni<List<String>> resolve(RepositoryContextRequest request) {
        return Uni.createFrom().item(() -> {
            List<String> fragments = new ArrayList<>();
            if (request.taskDescription() != null && !request.taskDescription().isBlank()) {
                fragments.add("Task Context: " + request.taskDescription());
            }
            for (String symbol : request.targetSymbols()) {
                var found = queryService.findSymbols(SymbolQuery.byName(symbol));
                if (!found.isEmpty()) {
                    fragments.add("Symbol " + symbol + " found at " + found.get(0).value().file());
                }
            }
            return fragments;
        });
    }
}
