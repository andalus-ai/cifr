package tech.kayys.aljabr.engine.index.indexer;

import tech.kayys.aljabr.engine.index.WorkspaceFingerprint;

public record IndexResult(
    int filesIndexed,
    int symbolsIndexed,
    int dependenciesIndexed,
    WorkspaceFingerprint fingerprint,
    long durationMs
) {}
