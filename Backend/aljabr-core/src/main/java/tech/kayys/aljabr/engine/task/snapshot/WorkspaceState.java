package tech.kayys.aljabr.engine.task.snapshot;

public record WorkspaceState(
    String repositoryId,
    String revision,
    String fingerprint
) {
    public static WorkspaceState of(String repositoryId, String revision) {
        return new WorkspaceState(repositoryId, revision, null);
    }
}
