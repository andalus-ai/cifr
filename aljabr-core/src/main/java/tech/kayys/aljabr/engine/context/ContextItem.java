package tech.kayys.aljabr.engine.context;

import tech.kayys.aljabr.engine.retrieval.CandidateSource;

/**
 * Materialized context item included in a ContextPacket with provenance.
 */
public record ContextItem(
    String id,
    String content,
    CandidateSource source,
    String sourceId,
    Representation representation,
    double relevance
) {
    public static ContextItem of(String id, String content, CandidateSource source, String sourceId, Representation rep, double relevance) {
        return new ContextItem(id, content, source, sourceId, rep, relevance);
    }
}
