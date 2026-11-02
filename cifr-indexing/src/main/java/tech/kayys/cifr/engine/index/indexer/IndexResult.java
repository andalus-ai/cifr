package tech.kayys.cifr.engine.index.indexer;

import tech.kayys.cifr.engine.index.WorkspaceFingerprint;

public record IndexResult(
    int filesIndexed,
    int symbolsIndexed,
    int dependenciesIndexed,
    WorkspaceFingerprint fingerprint,
    long durationMs
) {}
