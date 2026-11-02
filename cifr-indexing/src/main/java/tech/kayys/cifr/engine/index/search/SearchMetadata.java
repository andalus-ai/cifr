package tech.kayys.cifr.engine.index.search;

public record SearchMetadata(
    int totalMatches,
    boolean truncated,
    SearchStrategy strategyUsed
) {}
