package tech.kayys.cifr.engine.index.dependency;

/** Query constraints for dependency edges. */
public record DependencyRequest(
    String sourceIdentifier,
    DependencyKind targetKindFilter,
    String workspaceId
) {
    /** Compatibility constructor for callers that do not select a workspace. */
    public DependencyRequest(String sourceIdentifier, DependencyKind targetKindFilter) {
        this(sourceIdentifier, targetKindFilter, null);
    }
}
