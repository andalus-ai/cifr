package tech.kayys.aljabr.engine.index.search;

import java.util.List;

public record SearchResult(
    List<SearchMatch> matches,
    SearchMetadata metadata
) {}
