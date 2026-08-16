package tech.kayys.aljabr.engine.index.evidence;

public record EvidenceScore(
    String evidenceId,
    double relevance,
    double freshness,
    double confidence,
    double cost
) {
    public double computeTotalScore() {
        return relevance + freshness + confidence - cost;
    }
}
