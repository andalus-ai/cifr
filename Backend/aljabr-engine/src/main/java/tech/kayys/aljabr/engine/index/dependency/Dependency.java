package tech.kayys.aljabr.engine.index.dependency;

public record Dependency(
    String source,
    String target,
    DependencyKind kind
) {}
