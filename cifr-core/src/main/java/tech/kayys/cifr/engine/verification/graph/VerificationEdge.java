package tech.kayys.cifr.engine.verification.graph;

public record VerificationEdge(
    VerificationNode from,
    VerificationNode to,
    EdgeType type
) {}
