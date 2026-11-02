package tech.kayys.cifr.api.agent;

public record WorkspaceRevision(
    String commitHash,
    boolean isDirty
) {}
