package tech.kayys.aljabr.api.agent;

public record WorkspaceRevision(
    String commitHash,
    boolean isDirty
) {}
