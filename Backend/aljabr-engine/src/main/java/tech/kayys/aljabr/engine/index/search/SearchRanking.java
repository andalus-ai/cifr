package tech.kayys.aljabr.engine.index.search;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class SearchRanking {

    public static List<SearchMatch> rankAndLimit(List<SearchMatch> matches, int limit) {
        return matches.stream()
            .sorted(Comparator.comparingDouble(SearchMatch::score).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }
}
