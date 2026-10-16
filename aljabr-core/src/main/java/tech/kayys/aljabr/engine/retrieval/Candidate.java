package tech.kayys.aljabr.engine.retrieval;

/**
 * Atomic candidate item retrieved from memory, evidence, files, or external sources.
 */
public record Candidate(
    String id,
    CandidateSource source,
    String content,
    CandidateMetadata metadata
) {
    public static Candidate of(String id, CandidateSource source, String content, CandidateMetadata metadata) {
        return new Candidate(id, source, content, metadata);
    }
}
