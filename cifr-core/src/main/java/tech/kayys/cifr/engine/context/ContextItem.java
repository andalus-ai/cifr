package tech.kayys.cifr.engine.context;

import tech.kayys.cifr.engine.retrieval.CandidateSource;

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
