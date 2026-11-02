package tech.kayys.cifr.engine.retrieval;

import java.util.List;

/**
 * Scores and re-ranks retrieved candidates against the current objective query.
 */
public interface Reranker {

    List<RankedCandidate> rank(CandidateSet candidates, RetrievalQuery query);
}
