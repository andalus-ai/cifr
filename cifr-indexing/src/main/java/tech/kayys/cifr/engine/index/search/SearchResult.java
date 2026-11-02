package tech.kayys.cifr.engine.index.search;

import java.util.List;

public record SearchResult(
    List<SearchMatch> matches,
    SearchMetadata metadata
) {}
