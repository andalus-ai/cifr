package tech.kayys.cifr.engine.index.search;

import java.util.List;

public record SearchRequest(
    String query,
    SearchScope scope,
    int limit,
    List<SearchStrategy> preferredStrategies
) {}
