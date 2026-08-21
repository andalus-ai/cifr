package tech.kayys.aljabr.engine.retrieval;

import io.smallrye.mutiny.Uni;

/**
 * SPI adapter for retrieving candidates from a specific source (files, memory, evidence, tools).
 */
public interface Retriever {

    CandidateSource source();

    Uni<CandidateSet> retrieve(RetrievalQuery query);
}
