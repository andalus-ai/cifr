package tech.kayys.cifr.engine.retrieval;

import java.util.List;

/**
 * Collection of retrieved candidates prior to filtering and reranking.
 */
public record CandidateSet(List<Candidate> candidates) {
    public CandidateSet {
        candidates = candidates != null ? List.copyOf(candidates) : List.of();
    }

    public static CandidateSet of(List<Candidate> candidates) {
        return new CandidateSet(candidates);
    }

    public static CandidateSet empty() {
        return new CandidateSet(List.of());
    }

    public int size() {
        return candidates.size();
    }
}
