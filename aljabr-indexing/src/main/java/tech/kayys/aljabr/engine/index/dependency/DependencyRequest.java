package tech.kayys.aljabr.engine.index.dependency;

public record DependencyRequest(
    String sourceIdentifier,
    DependencyKind targetKindFilter
) {}
