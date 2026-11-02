package tech.kayys.cifr.engine.debug;

public record HypothesisScore(
    HypothesisId hypothesisId,
    double directEvidenceScore,
    double recentChangeScore,
    double structuralRelevanceScore,
    double contradictionPenalty,
    double totalScore
) {}
