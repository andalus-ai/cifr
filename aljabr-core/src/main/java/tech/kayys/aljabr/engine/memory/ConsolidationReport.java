package tech.kayys.aljabr.engine.memory;

import java.util.List;

/**
 * Summary report of memory consolidation executed across scopes.
 */
public record ConsolidationReport(
    String scopeId,
    int workingMemoriesEvaluated,
    int episodicPromotions,
    int semanticPromotions,
    int proceduralPromotions,
    int expiredOrInvalidated,
    List<String> consolidatedMemoryIds
) {
    public ConsolidationReport {
        consolidatedMemoryIds = consolidatedMemoryIds != null ? List.copyOf(consolidatedMemoryIds) : List.of();
    }
}
