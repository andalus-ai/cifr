package tech.kayys.aljabr.engine.eval;

public record EvaluationResult(
    double score,
    EvaluationDecision decision,
    String feedback
) {}
