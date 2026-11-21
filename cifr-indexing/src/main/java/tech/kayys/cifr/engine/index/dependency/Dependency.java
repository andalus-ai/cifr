package tech.kayys.cifr.engine.index.dependency;

/** A dependency edge originating in a workspace file. */
public record Dependency(
    String source,
    String target,
    DependencyKind kind,
    String workspaceId
) {
    public Dependency {
        if (workspaceId == null || workspaceId.isBlank()) {
            workspaceId = "default-workspace";
        }
    }

    /** Compatibility constructor for existing dependency producers. */
    public Dependency(String source, String target, DependencyKind kind) {
        this(source, target, kind, "default-workspace");
    }
}
