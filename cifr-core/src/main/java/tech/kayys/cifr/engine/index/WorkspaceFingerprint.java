package tech.kayys.cifr.engine.index;

public record WorkspaceFingerprint(
    String repositoryHash,
    String indexVersion,
    String configurationHash
) {}
