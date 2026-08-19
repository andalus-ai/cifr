package tech.kayys.aljabr.engine.verification.graph;

public record VerificationEdge(
    VerificationNode from,
    VerificationNode to,
    EdgeType type
) {}
