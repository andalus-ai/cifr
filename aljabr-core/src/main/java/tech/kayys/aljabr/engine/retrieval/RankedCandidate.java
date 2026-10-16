package tech.kayys.aljabr.engine.retrieval;

import java.util.List;

/**
 * Scored candidate item resulting from reranking.
 */
public record RankedCandidate(
    Candidate candidate,
    double score,
    List<String> reasons
) {
    public RankedCandidate {
        reasons = reasons != null ? List.copyOf(reasons) : List.of();
    }

    public static RankedCandidate of(Candidate candidate, double score, List<String> reasons) {
        return new RankedCandidate(candidate, score, reasons);
    }
}
