package tech.kayys.cifr.engine.eval;

public record EvaluationResult(
    double score,
    EvaluationDecision decision,
    String feedback
) {}
