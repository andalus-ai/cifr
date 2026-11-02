package tech.kayys.cifr.engine.retrieval;

/**
 * Eliminates redundant candidate items while preserving cross-source provenance.
 */
public interface CandidateDeduplicator {

    CandidateSet deduplicate(CandidateSet candidates);
}
