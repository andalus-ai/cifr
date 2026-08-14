package tech.kayys.aljabr.engine.index;

public record WorkspaceFingerprint(
    String repositoryHash,
    String indexVersion,
    String configurationHash
) {}
