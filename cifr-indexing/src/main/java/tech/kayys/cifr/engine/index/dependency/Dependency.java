package tech.kayys.cifr.engine.index.dependency;

public record Dependency(
    String source,
    String target,
    DependencyKind kind
) {}
