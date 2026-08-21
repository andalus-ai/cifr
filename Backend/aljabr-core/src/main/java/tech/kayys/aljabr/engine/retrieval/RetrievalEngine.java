package tech.kayys.aljabr.engine.retrieval;

import io.smallrye.mutiny.Uni;

/**
 * Coordinates multiple candidate retrievers and executes filtering and deduplication.
 */
public interface RetrievalEngine {

    Uni<CandidateSet> retrieve(RetrievalQuery query);
}
