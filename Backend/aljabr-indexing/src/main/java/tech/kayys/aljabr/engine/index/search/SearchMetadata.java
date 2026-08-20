package tech.kayys.aljabr.engine.index.search;

public record SearchMetadata(
    int totalMatches,
    boolean truncated,
    SearchStrategy strategyUsed
) {}
