package tech.kayys.cifr.engine.memory;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.cifr.engine.store.Memory;
import tech.kayys.cifr.engine.store.MemoryLifecycle;
import tech.kayys.cifr.engine.store.MemoryScope;
import tech.kayys.cifr.engine.store.MemoryService;
import tech.kayys.cifr.engine.store.MemoryType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Default deterministic keyword, scope, and metadata memory retriever.
 */
@ApplicationScoped
public class DefaultMemoryRetriever implements MemoryRetriever {

    @Inject
    MemoryService memoryService;

    @Inject
    MemoryRanker memoryRanker;

    public DefaultMemoryRetriever() {}

    public DefaultMemoryRetriever(MemoryService memoryService, MemoryRanker memoryRanker) {
        this.memoryService = memoryService;
        this.memoryRanker = memoryRanker;
    }

    @Override
    public Uni<List<MemoryMatch>> retrieve(MemoryQuery query) {
        if (query == null || query.query() == null || query.query().isBlank()) {
            return Uni.createFrom().item(List.of());
        }

        Set<MemoryScope> scopes = query.scopes().isEmpty() ? Set.of(MemoryScope.TASK, MemoryScope.PROJECT, MemoryScope.SESSION) : query.scopes();
        Set<MemoryType> types = query.types().isEmpty() ? Set.of(MemoryType.FACT, MemoryType.PREFERENCE, MemoryType.DECISION) : query.types();

        List<Uni<List<Memory>>> queries = new ArrayList<>();
        for (MemoryScope scope : scopes) {
            for (MemoryType type : types) {
                queries.add(memoryService.query(scope, type));
            }
        }

        if (queries.isEmpty()) {
            return Uni.createFrom().item(List.of());
        }

        return Uni.combine().all().unis(queries)
            .with(results -> {
                List<MemoryMatch> matches = new ArrayList<>();
                Set<String> seenIds = new HashSet<>();
                String lowerQuery = query.query().toLowerCase();

                for (Object res : results) {
                    if (res instanceof List<?> memList) {
                        for (Object o : memList) {
                            if (o instanceof Memory mem) {
                                if (mem.lifecycle() != MemoryLifecycle.ACTIVE || !seenIds.add(mem.id())) {
                                    continue;
                                }

                                List<MatchReason> reasons = new ArrayList<>();
                                double score = mem.importance();

                                if (mem.content().toLowerCase().contains(lowerQuery)) {
                                    score += 0.5;
                                    reasons.add(MatchReason.of("EXACT_KEYWORD_MATCH", 0.5));
                                }

                                if (query.scopes().contains(mem.scope())) {
                                    score += 0.3;
                                    reasons.add(MatchReason.of("EXACT_SCOPE_MATCH", 0.3));
                                }

                                matches.add(MemoryMatch.of(mem, score, reasons));
                            }
                        }
                    }
                }

                List<MemoryMatch> ranked = memoryRanker.rank(matches);
                if (query.limit() > 0 && ranked.size() > query.limit()) {
                    return ranked.subList(0, query.limit());
                }
                return ranked;
            });
    }
}
