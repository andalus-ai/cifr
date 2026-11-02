package tech.kayys.cifr.engine.index.dependency;

public record DependencyRequest(
    String sourceIdentifier,
    DependencyKind targetKindFilter
) {}
